package io.openrouter.android.auto.sample.xml

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import io.openrouter.android.auto.OpenRouterModel
import io.openrouter.android.auto.isFreeModel

class ModelAdapter(
    private val onSelect: (OpenRouterModel) -> Unit
) : RecyclerView.Adapter<ModelAdapter.ViewHolder>() {

    private var items = listOf<OpenRouterModel>()

    fun submitList(newItems: List<OpenRouterModel>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_model, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(items[position])

    override fun getItemCount() = items.size

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val textName: TextView = view.findViewById(R.id.textModelName)
        private val textProvider: TextView = view.findViewById(R.id.textModelProvider)
        private val textContext: TextView = view.findViewById(R.id.textContextLength)
        private val textPrice: TextView = view.findViewById(R.id.textModelPrice)

        fun bind(model: OpenRouterModel) {
            textName.text = model.name
            textProvider.text = model.id.substringBefore("/")
            textContext.text = "${model.contextLength / 1000}K ctx"
            textPrice.text = if (isFreeModel(model)) "Free" else "$${model.pricing.prompt}/tok"
            itemView.setOnClickListener { onSelect(model) }
        }
    }
}
