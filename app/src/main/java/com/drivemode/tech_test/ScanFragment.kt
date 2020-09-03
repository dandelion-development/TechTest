package com.drivemode.tech_test

import android.bluetooth.BluetoothDevice
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.polidea.rxandroidble2.RxBleClient
import com.polidea.rxandroidble2.scan.ScanSettings
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import java.util.concurrent.TimeUnit

interface DeviceItemClickListener {
    fun onDiscoverButtonClick(Product: BluetoothDevice)
}

class ScanFragment : Fragment(), DeviceItemClickListener {


    private var devices = mutableListOf<BluetoothDevice>()
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: DevicesAdapter
    private lateinit var rxBleClient: RxBleClient
    private val subscriptions: CompositeDisposable = CompositeDisposable()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_scan, container, false)
    }

    override fun onStart() {
        super.onStart()
        val layoutManager = LinearLayoutManager(activity)
        layoutManager.orientation = RecyclerView.VERTICAL
        recyclerView = requireActivity().findViewById(R.id.devices_list)
        recyclerView.layoutManager = layoutManager
        adapter = DevicesAdapter(devices, this)
        recyclerView.adapter = adapter
        rxBleClient = RxBleClient.create(requireContext())
    }

    override fun onResume() {
        super.onResume()
        subscriptions += scanDevices()
                .buffer(1000, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ adapter.updateDevices(it) }, { Log.e("ScanFragment", it.message) })
    }


    private fun showProgress() {
        val progress = view?.findViewById<ProgressBar>(R.id.progress);
        progress?.visibility = View.VISIBLE;
    }

    fun hideProgress() {
        val progress = view?.findViewById<ProgressBar>(R.id.progress);
        progress?.visibility = View.INVISIBLE;
    }

    private fun scanDevices(): Observable<BluetoothDevice> {
        return rxBleClient.observeStateChanges()
                .startWith(rxBleClient.state)
                .switchMap { state ->
                    when (state) {
                        RxBleClient.State.READY -> {
                            val scanSettings: ScanSettings = ScanSettings.Builder()
                                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                                    .build()
                            rxBleClient.scanBleDevices(scanSettings)
                                    .map { it.bleDevice.bluetoothDevice }
                        }
                        else -> Observable.empty()
                    }
                }
                .doOnError {
                    Log.e("ScanFragment", "Error scanning devices")
                }
    }

    fun testToast() {
        Toast.makeText(context, "test", Toast.LENGTH_LONG).show()
    }

    override fun onDiscoverButtonClick(device: BluetoothDevice) {
        // In order make easier follow application events by user services scanning was moved to DisplayFragment
        val displayFragment = DisplayFragment()
        val bundle = Bundle()
        bundle.putString("DEVICE_ADDRESS", device.address)
        displayFragment.arguments = bundle
        activity!!.supportFragmentManager
                .beginTransaction()
                .replace(R.id.frameContainer, displayFragment)
                .addToBackStack(null)
                .show(displayFragment)
                .commit()
    }

    inner class DevicesAdapter(
            var devices: MutableList<BluetoothDevice>,
            var listener: DeviceItemClickListener
    ) : RecyclerView.Adapter<DevicesAdapter.DevicesViewHolder>() {


        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DevicesViewHolder {
            val rootView: View = LayoutInflater.from(parent.context).inflate(R.layout.device_item, parent, false)
            return DevicesViewHolder(rootView)
        }

        fun updateDevices(scannedDevices: MutableList<BluetoothDevice>) {
            hideProgress()
            scannedDevices.forEach {
                if (!devices.contains(it)) devices.add(it)
            }
            notifyDataSetChanged()
        }

        override fun getItemCount(): Int {
            return devices.size
        }

        override fun onBindViewHolder(holder: DevicesViewHolder, position: Int) {
            holder.bindItem(devices[position])
        }


        inner class DevicesViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener {
            private val name: TextView = itemView.findViewById(R.id.device_name)
            private val mac: TextView = itemView.findViewById(R.id.device_mac)
            private val button: Button = itemView.findViewById(R.id.connect_button)

            init {
                button.setOnClickListener(this)
            }

            fun bindItem(device: BluetoothDevice) {
                name.text = device.name
                mac.text = device.address
            }

            override fun onClick(v: View?) {
                when (v!!.id) {
                    R.id.connect_button -> listener.onDiscoverButtonClick(devices[adapterPosition])
                    else -> Log.e("ScanFragment", "OnClick event received for an unknown view Id ")
                }
            }
        }
    }
}