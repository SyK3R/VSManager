package com.vsmanager

import java.io.*
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors

class SimpleFtpServer(
    private val port: Int,
    rootDir: File,
    private val user: String,
    private val pass: String,
    private val anonymous: Boolean,
    private val readOnly: Boolean
) {
    private val root = rootDir.canonicalFile
    private var server: ServerSocket? = null
    private val pool = Executors.newCachedThreadPool()
    @Volatile private var running = false

    fun start() {
        val s = ServerSocket()
        s.reuseAddress = true
        s.bind(InetSocketAddress(port))
        server = s
        running = true
        Thread {
            while (running) {
                try {
                    val c = s.accept()
                    pool.execute { Session(c).run() }
                } catch (e: Exception) { }
            }
        }.start()
    }

    fun stop() {
        running = false
        try { server?.close() } catch (_: Exception) {}
        pool.shutdownNow()
    }

    private inner class Session(val ctl: Socket) {
        private val input = BufferedReader(InputStreamReader(ctl.getInputStream(), Charsets.UTF_8))
        private val outp = ctl.getOutputStream()
        private var cwd = "/"
        private var gotUser = false
        private var authed = false
        private var pasv: ServerSocket? = null
        private var rnfr: File? = null
        private var restPos = 0L

        private fun send(s: String) {
            outp.write((s + "\r\n").toByteArray(Charsets.UTF_8)); outp.flush()
        }

        private fun virt(f: File): String {
            val rel = f.path.removePrefix(root.path)
            return if (rel.isEmpty()) "/" else rel
        }

        private fun resolve(arg: String): File? {
            val v = if (arg.startsWith("/")) arg else if (cwd == "/") "/$arg" else "$cwd/$arg"
            val f = File(root, v).canonicalFile
            return if (f.path == root.path || f.path.startsWith(root.path + File.separator)) f else null
        }

        private fun closePasv() { try { pasv?.close() } catch (_: Exception) {}; pasv = null }

        private fun openData(): Socket? {
            val p = pasv ?: return null
            return try { p.soTimeout = 30_000; p.accept() } catch (e: Exception) { null } finally { closePasv() }
        }

        fun run() {
            try {
                ctl.soTimeout = 300_000
                send("220 VSManager FTP pronto")
                while (true) {
                    val line = input.readLine() ?: break
                    val sp = line.indexOf(' ')
                    val cmd = (if (sp < 0) line else line.substring(0, sp)).uppercase()
                    val arg = if (sp < 0) "" else line.substring(sp + 1).trim()
                    if (!handle(cmd, arg)) break
                }
            } catch (_: Exception) {
            } finally {
                closePasv()
                try { ctl.close() } catch (_: Exception) {}
            }
        }

        private fun handle(cmd: String, arg: String): Boolean {
            when (cmd) {
                "USER" -> { gotUser = anonymous || arg == user; authed = false; send("331 Senha necessaria"); return true }
                "PASS" -> {
                    authed = gotUser && (anonymous || arg == pass)
                    send(if (authed) "230 Autenticado" else "530 Login incorreto"); return true
                }
                "QUIT" -> { send("221 Ate logo"); return false }
                "SYST" -> { send("215 UNIX Type: L8"); return true }
                "FEAT" -> { send("211-Recursos\r\n SIZE\r\n UTF8\r\n211 Fim"); return true }
                "OPTS", "NOOP", "TYPE", "MODE", "STRU" -> { send("200 OK"); return true }
            }
            if (!authed) { send("530 Faca login primeiro"); return true }

            if (readOnly && cmd in setOf("STOR", "APPE", "DELE", "RMD", "XRMD", "MKD", "XMKD", "RNFR", "RNTO")) {
                send("550 Servidor em modo somente leitura"); return true
            }

            when (cmd) {
                "PWD", "XPWD" -> send("257 \"$cwd\"")
                "CWD" -> {
                    val f = resolve(arg)
                    if (f != null && f.isDirectory) { cwd = virt(f); send("250 OK") } else send("550 Pasta invalida")
                }
                "CDUP" -> {
                    val f = resolve("..")
                    if (f != null) { cwd = virt(f); send("250 OK") } else send("550 Erro")
                }
                "PASV" -> {
                    closePasv()
                    val s = ServerSocket(0); pasv = s
                    val ip = ctl.localAddress.hostAddress?.replace('.', ',') ?: "127,0,0,1"
                    send("227 Modo passivo ($ip,${s.localPort / 256},${s.localPort % 256})")
                }
                "EPSV" -> {
                    closePasv()
                    val s = ServerSocket(0); pasv = s
                    send("229 Modo passivo estendido (|||${s.localPort}|)")
                }
                "LIST", "NLST" -> {
                    val target = if (arg.isEmpty() || arg.startsWith("-")) resolve(cwd) else resolve(arg)
                    if (target == null || !target.exists()) { send("550 Nao encontrado"); return true }
                    val items = if (target.isDirectory) (target.listFiles()?.toList() ?: emptyList()) else listOf(target)
                    val text = items.sortedBy { it.name.lowercase() }
                        .joinToString("") { (if (cmd == "NLST") it.name else listLine(it)) + "\r\n" }
                    sendText(text)
                }
                "RETR" -> {
                    val f = resolve(arg)
                    if (f == null || !f.isFile) { send("550 Arquivo nao encontrado"); return true }
                    send("150 Enviando")
                    val ds = openData() ?: run { send("425 Sem conexao de dados"); return true }
                    try {
                        FileInputStream(f).use { fi ->
                            if (restPos > 0) fi.skip(restPos)
                            fi.copyTo(ds.getOutputStream(), 65536)
                        }
                        send("226 Concluido")
                    } catch (e: Exception) { send("426 Transferencia abortada") }
                    finally { try { ds.close() } catch (_: Exception) {}; restPos = 0 }
                }
                "STOR", "APPE" -> {
                    val f = resolve(arg)
                    if (f == null || f.isDirectory) { send("550 Caminho invalido"); return true }
                    send("150 Recebendo")
                    val ds = openData() ?: run { send("425 Sem conexao de dados"); return true }
                    try {
                        FileOutputStream(f, cmd == "APPE").use { fo -> ds.getInputStream().copyTo(fo, 65536) }
                        send("226 Concluido")
                    } catch (e: Exception) { send("451 Erro ao gravar: ${e.message}") }
                    finally { try { ds.close() } catch (_: Exception) {} }
                }
                "DELE" -> {
                    val f = resolve(arg)
                    send(if (f != null && f.isFile && f.delete()) "250 Removido" else "550 Nao foi possivel remover")
                }
                "RMD", "XRMD" -> {
                    val f = resolve(arg)
                    send(if (f != null && f != root && f.isDirectory && f.delete()) "250 Removido" else "550 Nao foi possivel remover")
                }
                "MKD", "XMKD" -> {
                    val f = resolve(arg)
                    if (f != null && f.mkdirs()) send("257 \"${virt(f)}\" criada") else send("550 Nao foi possivel criar")
                }
                "RNFR" -> {
                    val f = resolve(arg)
                    if (f != null && f.exists()) { rnfr = f; send("350 Informe o novo nome") } else send("550 Nao encontrado")
                }
                "RNTO" -> {
                    val from = rnfr; val to = resolve(arg)
                    send(if (from != null && to != null && from.renameTo(to)) "250 Renomeado" else "550 Falhou")
                    rnfr = null
                }
                "SIZE" -> {
                    val f = resolve(arg)
                    if (f != null && f.isFile) send("213 ${f.length()}") else send("550 Nao encontrado")
                }
                "MDTM" -> {
                    val f = resolve(arg)
                    if (f != null && f.exists()) {
                        val fmt = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
                        send("213 ${fmt.format(Date(f.lastModified()))}")
                    } else send("550 Nao encontrado")
                }
                "REST" -> { restPos = arg.toLongOrNull() ?: 0L; send("350 Posicao definida") }
                "ABOR" -> { closePasv(); send("226 Abortado") }
                else -> send("502 Comando nao suportado")
            }
            return true
        }

        private fun sendText(text: String) {
            send("150 Abrindo conexao de dados")
            val ds = openData() ?: run { send("425 Sem conexao de dados"); return }
            try {
                ds.getOutputStream().use { it.write(text.toByteArray(Charsets.UTF_8)) }
                send("226 Concluido")
            } catch (e: Exception) { send("426 Transferencia abortada") }
            finally { try { ds.close() } catch (_: Exception) {} }
        }

        private fun listLine(f: File): String {
            val recent = System.currentTimeMillis() - f.lastModified() < 180L * 86_400_000
            val fmt = SimpleDateFormat(if (recent) "MMM dd HH:mm" else "MMM dd  yyyy", Locale.US)
            return (if (f.isDirectory) "drwxr-xr-x" else "-rw-r--r--") +
                " 1 ftp ftp " + f.length().toString().padStart(12) + " " +
                fmt.format(Date(f.lastModified())) + " " + f.name
        }
    }
}
