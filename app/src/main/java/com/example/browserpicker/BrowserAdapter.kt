package com.example.browserpicker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * Simple RecyclerView adapter showing browser apps in a list.
 */
class BrowserAdapter(
    private val browsers: List<BrowserInfo>,
    private val onItemClick: (BrowserInfo) -> Unit
) : RecyclerView.Adapter<BrowserAdapter.BrowserViewHolder>() {

    inner class BrowserViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(android.R.id.icon)
        val name: TextView = view.findViewById(android.R.id.text1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BrowserViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return BrowserViewHolder(view)
    }

    override fun onBindViewHolder(holder: BrowserViewHolder, position: Int) {
        val browser = browsers[position]
        holder.icon.setImageDrawable(browser.icon)
        holder.name.text = browser.label
        holder.itemView.setOnClickListener { onItemClick(browser) }
    }

    override fun getItemCount(): Int = browsers.size
}
