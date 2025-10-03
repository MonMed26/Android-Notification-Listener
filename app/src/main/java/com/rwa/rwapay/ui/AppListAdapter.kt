package com.rwa.rwapay.ui

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.rwa.rwapay.R

data class AppItem(
    val label: String,
    val packageName: String,
    val icon: Drawable?,
    var enabled: Boolean
)

class AppListAdapter(
    private val items: MutableList<AppItem>,
    private val onToggle: (AppItem, Boolean) -> Unit
) : RecyclerView.Adapter<AppListAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val icon: ImageView = v.findViewById(R.id.appIcon)
        val name: TextView = v.findViewById(R.id.appName)
        val pkg: TextView = v.findViewById(R.id.appPackage)
        val sw: Switch = v.findViewById(R.id.appSwitch)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_toggle, parent, false)
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val it = items[position]
        holder.icon.setImageDrawable(it.icon)
        holder.name.text = it.label
        holder.pkg.text = it.packageName
        holder.sw.setOnCheckedChangeListener(null)
        holder.sw.isChecked = it.enabled
        holder.sw.setOnCheckedChangeListener { _, checked ->
            it.enabled = checked
            onToggle(it, checked)
        }
    }

    fun replaceAll(newItems: List<AppItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}
