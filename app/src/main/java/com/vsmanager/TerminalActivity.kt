package com.vsmanager

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader
import java.io.InputStreamReader

class TerminalActivity : AppCompatActivity() {

    private lateinit var outputText: TextView
    private lateinit var commandInput: EditText
    private lateinit var scrollView: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_terminal)

        outputText = findViewById(R.id.outputText)
        commandInput = findViewById(R.id.commandInput)
        scrollView = findViewById(R.id.scrollView)
        val executeButton = findViewById<Button>(R.id.executeButton)

        outputText.text = "Terminal VSManager\nDigite comandos para executar no sistema.\nEx: rm -rf /sdcard/Teste\n\n"

        executeButton.setOnClickListener {
            val command = commandInput.text.toString().trim()
            if (command.isNotEmpty()) {
                // Alerta para comandos perigosos
                if (command.contains("rm -rf") || command.contains("format") || command.contains("mkfs")) {
                    AlertDialog.Builder(this)
                        .setTitle("Cuidado!")
                        .setMessage("Você está prestes a executar um comando destrutivo:\n\n$command\n\nTem certeza?")
                        .setPositiveButton("Executar") { _, _ ->
                            runCommand(command)
                        }
                        .setNegativeButton("Cancelar", null)
                        .show()
                } else {
                    runCommand(command)
                }
                commandInput.setText("")
            }
        }

        // Para rodar ao apertar "Enter" no teclado
        commandInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                executeButton.performClick()
                true
            } else false
        }
    }

    private fun appendOutput(text: String) {
        outputText.append(text)
        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun runCommand(command: String) {
        appendOutput("\n$ $command\n")
        
        Thread {
            try {
                // Executa com Shizuku (se disponível) ou como usuário normal
                val process = try {
                    val shizukuClass = Class.forName("rikka.shizuku.Shizuku")
                    val newProcessMethod = shizukuClass.getDeclaredMethod(
                        "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
                    )
                    newProcessMethod.isAccessible = true
                    // O segredo está aqui: passar "sh", "-c", command
                    newProcessMethod.invoke(null, arrayOf("sh", "-c", command), null, null) as rikka.shizuku.ShizukuRemoteProcess
                } catch (e: Exception) {
                    Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
                }

                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    runOnUiThread { appendOutput("$line\n") }
                }

                val errorReader = BufferedReader(InputStreamReader(process.errorStream))
                while (errorReader.readLine().also { line = it } != null) {
                    runOnUiThread { appendOutput("$line\n") }
                }
                
                process.waitFor()
                runOnUiThread { appendOutput("Concluído.\n") }
            } catch (e: Exception) {
                runOnUiThread { appendOutput("Erro: ${e.message}\n") }
            }
        }.start()
    }
}
