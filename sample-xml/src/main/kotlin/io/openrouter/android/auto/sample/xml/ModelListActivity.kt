package io.openrouter.android.auto.sample.xml

import android.content.Intent
import android.os.Bundle
import android.widget.SearchView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.openrouter.android.auto.sample.xml.viewmodel.ModelListViewModel
import kotlinx.coroutines.launch

class ModelListActivity : AppCompatActivity() {

    private lateinit var viewModel: ModelListViewModel
    private lateinit var adapter: ModelAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_model_list)

        val sdk = (application as App).openRouter ?: run {
            finish()
            return
        }

        viewModel = ViewModelProvider(
            this,
            ModelListViewModel.Factory(sdk)
        )[ModelListViewModel::class.java]

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerModels)
        val searchView = findViewById<SearchView>(R.id.searchModels)
        val textStatus = findViewById<TextView>(R.id.textStatus)

        adapter = ModelAdapter { model ->
            val intent = Intent(this, ChatActivity::class.java).apply {
                putExtra("model_id", model.id)
                putExtra("model_name", model.name)
            }
            startActivity(intent)
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))
        recyclerView.adapter = adapter

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false
            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.setSearch(newText ?: "")
                return true
            }
        })

        lifecycleScope.launch {
            viewModel.models.collect { models ->
                adapter.submitList(models)
                textStatus.text = "${models.size} models available"
            }
        }
    }
}
