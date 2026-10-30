package com.vsmanager

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader
import java.io.InputStreamReader

class TerminalActivity : AppCompatActivity() {

    private lateinit var outputText: TextView
    private lateinit var commandInput: EditText
    private lateinit var scrollView: ScrollView
    private val history = StringBuilder() // Buffer para não travar a tela

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_terminal)

        outputText = findViewById(R.id.outputText)
        commandInput = findViewById(R.id.commandInput)
        scrollView = findViewById(R.id.scrollView)
        val executeButton = findViewById<Button>(R.id.executeButton)

        // Histórico inicial
        history.append("Terminal VSManager (Shizuku)\n")
        history.append("Digite comandos para executar no sistema.\nEx: ls /sdcard/\n\n")
        outputText.text = history.toString()

        executeButton.setOnClickListener {
            val command = commandInput.text.toString().trim()
            if (command.isNotEmpty()) {
                // Se o comando for destrutivo, pede confirmação
                if (command.contains("rm -rf") || command.contains("uninstall") || command.contains("format")) {
                    AlertDialog.Builder(this)
                        .setTitle("Cuidado!")
                        .setMessage("Você está prestes a executar um comando destrutivo:\n\n$command\n\nTem certeza?")
                        .setPositiveButton("Executar") { _, _ -> runCommand(command) }
                        .setNegativeButton("Cancelar", null)
                        .show()
                } else {
                    runCommand(command)
                }
                commandInput.setText("")
            }
        }

        commandInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                executeButton.performClick()
                true
            } else false
        }
    }

    private fun runCommand(command: String) {
        history.append("\n$ $command\n")
        outputText.text = history.toString() // Mostra o comando digitado na hora
        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }

        Thread {
            try {
                val process = try {
                    val shizukuClass = Class.forName("rikka.shizuku.Shizuku")
                    val newProcessMethod = shizukuClass.getDeclaredMethod(
                        "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
                    )
                    newProcessMethod.isAccessible = true
                    newProcessMethod.invoke(null, arrayOf("sh", "-c", command), null, null) as rikka.shizuku.ShizukuRemoteProcess
                } catch (e: Exception) {
                    Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
                }

                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    history.append("$line\n") // Só adiciona no buffer
                }

                val errorReader = BufferedReader(InputStreamReader(process.errorStream))
                while (errorReader.readLine().also { line = it } != null) {
                    history.append("$line\n") // Só adiciona no buffer
                }
                
                process.waitFor()
                
                // Atualiza a tela UMA VEZ SÓ no final
                runOnUiThread {
                    outputText.text = history.toString()
                    scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                }
            } catch (e: Exception) {
                history.append("Erro: ${e.message}\n")
                runOnUiThread {
                    outputText.text = history.toString()
                    scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                }
            }
        }.start()
    }
}
