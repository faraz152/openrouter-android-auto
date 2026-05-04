package io.openrouter.android.auto.sample.xml

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.openrouter.android.auto.ORAError
import kotlinx.coroutines.launch

class ApiKeyActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val prefs = getSharedPreferences("sample_xml_prefs", MODE_PRIVATE)
        val savedKey = prefs.getString("api_key", "") ?: ""

        val editApiKey = findViewById<EditText>(R.id.editApiKey)
        val buttonConnect = findViewById<Button>(R.id.buttonConnect)

        editApiKey.setText(savedKey)

        buttonConnect.setOnClickListener {
            val apiKey = editApiKey.text.toString().trim()
            if (apiKey.isBlank()) {
                editApiKey.error = "API key is required"
                return@setOnClickListener
            }

            prefs.edit().putString("api_key", apiKey).apply()
            buttonConnect.isEnabled = false
            buttonConnect.text = "Connecting…"

            val app = application as App
            app.initSdk(apiKey)

            lifecycleScope.launch {
                try {
                    app.openRouter?.initialize()
                    startActivity(Intent(this@ApiKeyActivity, ModelListActivity::class.java))
                } catch (e: ORAError) {
                    Toast.makeText(this@ApiKeyActivity, e.message, Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(this@ApiKeyActivity, "Connection failed: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    buttonConnect.isEnabled = true
                    buttonConnect.text = "Connect"
                }
            }
        }
    }
}
