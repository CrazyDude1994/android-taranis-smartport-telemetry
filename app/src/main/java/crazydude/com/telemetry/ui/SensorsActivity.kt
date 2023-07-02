package crazydude.com.telemetry.ui

import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import crazydude.com.telemetry.R
import crazydude.com.telemetry.manager.PreferenceManager
import crazydude.com.telemetry.ui.adapters.SensorsAdapter
import crazydude.com.telemetry.ui.adapters.SensorsAdapterListener
import uk.co.deanwild.materialshowcaseview.MaterialShowcaseView

class SensorsActivity : AppCompatActivity(), SensorsAdapterListener {

    private lateinit var adapter: SensorsAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var preferenceManager: PreferenceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_sensors)

        preferenceManager = PreferenceManager(this)

        val sensorsSettings = preferenceManager.getSensorsSettings()

        adapter =
            SensorsAdapter(sensorsSettings.filter { it.position == "top" }.sortedBy { it.index }
                .map {
                    Sensor(
                        it.name,
                        it.shown
                    )
                },
                sensorsSettings.filter { it.position == "bottom" }.sortedBy { it.index }.map {
                    Sensor(
                        it.name,
                        it.shown
                    )
                }, this
            )

        recyclerView = findViewById(R.id.recycler_view)

        recyclerView.adapter = adapter
        val linearLayoutManager = LinearLayoutManager(this)
        recyclerView.layoutManager = linearLayoutManager
        val touchHelper =
            ItemTouchHelper(object :
                ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder
                ): Boolean {
                    adapter.moveItem(viewHolder.adapterPosition, target.adapterPosition)
                    return true
                }

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {

                }

                override fun onSelectedChanged(
                    viewHolder: RecyclerView.ViewHolder?,
                    actionState: Int
                ) {
                    viewHolder?.itemView?.setBackgroundColor(Color.GRAY)
                }

                override fun clearView(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder
                ) {
                    super.clearView(recyclerView, viewHolder)
                    viewHolder.itemView.setBackgroundColor(Color.TRANSPARENT)
                }

                override fun canDropOver(
                    recyclerView: RecyclerView,
                    current: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder
                ): Boolean {
                    return target.adapterPosition != 0
                }

                override fun getDragDirs(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder
                ): Int {
                    if (adapter.getItemViewType(viewHolder.adapterPosition) == 1) return 0
                    return super.getDragDirs(recyclerView, viewHolder)
                }

            })
        touchHelper.attachToRecyclerView(recyclerView)

        recyclerView.viewTreeObserver.addOnGlobalLayoutListener {
            //if all sensors are moved to bottom bar, search target in bottom section
            var target = linearLayoutManager.findViewByPosition(1)?.findViewById<View>(R.id.move) ?: linearLayoutManager.findViewByPosition(2)?.findViewById<View>(R.id.move)
            //GlobalLayout event is also fired when user is trying to move sensor.
            //At this point a list might be scrolled far to bottom, and upper list item views can be deleted (because they are out of screen).
            //As ShowcaseView should has been shown already, just test for target !=null

            if ( target !== null)
            {
                MaterialShowcaseView.Builder(this)
                    .renderOverNavigationBar()
                    .setTarget(target)
                    .setMaskColour(Color.argb(180, 0, 0, 0))
                    .setDismissText("GOT IT")
                    .singleUse("sensors_guide1")
                    .setContentText("You can drag sensor to change sensor order")
                    .show()
            }
        }

    }

    override fun onSettingsClick(index: Int) {
    }

    override fun onStop() {
        super.onStop()
        preferenceManager.setSensorsSettings(adapter.getSensorsList())
    }

    data class Sensor(val name: String, var isShown: Boolean = true, var isDragged: Boolean = false)
}