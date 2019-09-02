package crazydude.com.telemetry.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import crazydude.com.telemetry.R
import crazydude.com.telemetry.ui.SensorsActivity

class SensorsAdapter(topSensors: List<SensorsActivity.Sensor>, bottomSensors: List<SensorsActivity.Sensor>) : RecyclerView.Adapter<SensorsAdapter.ViewHolder>() {

    val data: ArrayList<Any> = ArrayList()

    init {
        data.add("Top bar")
        data.addAll(topSensors)
        data.add("Bottom bar")
        data.addAll(bottomSensors)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return if (viewType == 0) ViewHolder(
            LayoutInflater.from(parent.context).inflate(
                R.layout.view_sensor,
                parent,
                false
            )
        ) else
            ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.view_sensor_header, parent, false))
    }

    override fun getItemCount(): Int {
        return data.size
    }

    override fun getItemViewType(position: Int): Int {
        return if (data[position] is SensorsActivity.Sensor) 0 else 1
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        if (getItemViewType(position) == 0) {
            val sensor = data[position] as SensorsActivity.Sensor
            holder.switch!!.text = sensor.name
            holder.switch.setOnCheckedChangeListener { compoundButton, value ->
                sensor.isShown = value
            }
        } else {
            (holder.itemView as TextView).text = (data[position] as String)
        }
    }

    fun moveItem(oldIndex: Int, newIndex: Int) {
        val old = data[oldIndex]
        data[oldIndex] = data[newIndex]
        data[newIndex] = old
        notifyItemMoved(oldIndex, newIndex)
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val switch: CheckBox? = view.findViewById(R.id.sensor_view)
    }
}