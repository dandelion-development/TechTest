package com.drivemode.tech_test

import android.content.Context
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import com.polidea.rxandroidble2.RxBleClient
import com.polidea.rxandroidble2.RxBleConnection
import com.polidea.rxandroidble2.scan.ScanSettings
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import java.util.*
import java.util.concurrent.TimeUnit

class DisplayFragment : Fragment() {

    private val TAG = MainActivity::class.java.simpleName
    private var serviceList: List<Pair<String, String>> = emptyList()
    private lateinit var rxBleClient: RxBleClient
    private lateinit var discoveredList: Array<String>
    private var servicesCount: Int = 0
    private var deviceAddress: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //Gets device address from arguments
        arguments?.getString("DEVICE_ADDRESS")?.let {
            deviceAddress = it
        }
        // Initializes RxBleClient
        rxBleClient = RxBleClient.create(requireContext())
        discoverServices(deviceAddress)
    }

    // Is called after try discover devices and notifies if discovery returns zero services
    private fun afterDiscover() {
        if (servicesCount <= 0) {
            hideProgress()
            Toast.makeText(requireContext(), "No services found !", Toast.LENGTH_LONG).show()
        }
    }

    private fun discoverServices(deviceAddress: String) {
        showProgress()
        // Attempts to discover devices
        val device = rxBleClient.getBleDevice(deviceAddress)
        device.establishConnection(false)
                .flatMapSingle { it.discoverServices() }
                .take(12, TimeUnit.SECONDS)
                .subscribe({ services ->
                    discoveredList = services.bluetoothGattServices.map { it.uuid.toString() }.toTypedArray()
                    servicesCount = services.bluetoothGattServices.size
                    DownloadTask(this).execute()

                }, {
                    Log.e("ScanFragment", "Error discovering services: ${it.message}")
                })
        // Connection state is observed in order to trigger notifications on connection is closed
        val observingConnectionStateDisposable = device.observeConnectionStateChanges()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { connectionState ->
                            // On connection is disconnected
                            if (connectionState == RxBleConnection.RxBleConnectionState.DISCONNECTED) {
                                // Performs after discover tasks (progressBar update && Toast message)
                                afterDiscover()
                            }
                        },
                        { throwable -> Log.d("Error: ", throwable.toString()) }
                )
        /*
        rxBleClient.getBleDevice(deviceAddress)
                .establishConnection(false)
                .flatMapSingle { it.discoverServices() }
                .take(1)
                .subscribe({ services ->
                    discoveredList = services.bluetoothGattServices.map { it.uuid.toString() }.toTypedArray()
                    servicesCount = services.bluetoothGattServices.size
                    DownloadTask(this).execute()

                }, {
                    Log.e("ScanFragment", "Error discovering services: ${it.message}")
                })

         */
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_display, container, false)


    }

    override fun onResume() {
        super.onResume()
        //DownloadTask(this).doInBackground()
    }

    fun setData(array: JSONArray) {
        serviceList = convert(array)
        val adapter = MyAdapter(activity!!, discoveredList.toList() ?: emptyList())
        val listView = view?.findViewById<ListView>(R.id.list)
        listView?.adapter = adapter
    }

    fun convert(array: JSONArray): List<Pair<String, String>> {
        val list = ArrayList<Pair<String, String>>(array.length())
        var i = 0
        val count = array.length()
        while (i < count) {
            try {
                val `object` = array.getJSONObject(i)
                val uuid = `object`!!.optString("uuid")
                val name = `object`!!.optString("name")
                list.add(Pair(uuid, name))
            } catch (e: JSONException) {
                Log.e(TAG, "invalid json", e)
            }
            i++
        }
        return list
    }

    fun showProgress() {
        val progress = view?.findViewById<ProgressBar>(R.id.progress);
        progress?.setVisibility(View.VISIBLE);
    }

    fun hideProgress() {
        val progress = view?.findViewById<ProgressBar>(R.id.progress);
        progress?.setVisibility(View.INVISIBLE);
    }

    private inner class DownloadTask(private val fragment: DisplayFragment) : AsyncTask<Void, Void, JSONObject>() {

        public override fun doInBackground(vararg params: Void?): JSONObject {
            fragment.showProgress()
            var reader: BufferedReader? = null
            var connection: HttpURLConnection? = null

            try {
                val url = URL("https://run.mocky.io/v3/59845319-235d-4326-ac9e-61552792bc21")
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connect()
                reader = BufferedReader(InputStreamReader(connection.inputStream))
                val builder = StringBuilder()
                var line = reader.readLine()
                while (line != null) {
                    builder.append(line)
                    line = reader.readLine()
                }
                return JSONObject(builder.toString())
            } catch (e: MalformedURLException) {
                Log.e(TAG, "malformed url", e)
            } catch (e: IOException) {
                Log.e(TAG, "io error", e)
            } catch (e: JSONException) {
                Log.e(TAG, "invalid json", e)
            } finally {
                try {
                    reader?.close()
                } catch (e: IOException) {
                    Log.e(TAG, "", e)
                }
                connection?.disconnect()
            }
            // Progressbar should be hidden after process execution
            // * hide event is moved to onPostExecute
            //fragment.hideProgress()
            return JSONObject()
        }

        override fun onPostExecute(json: JSONObject) {
            super.onPostExecute(json)
            try {
                fragment.setData(json.getJSONArray("services"))
                // After get services hides progress bar and shows information message
                fragment.hideProgress()
                Toast.makeText(context, "${servicesCount} Services found!", Toast.LENGTH_SHORT).show()
            } catch (e: JSONException) {
                Log.e(TAG, "services field not found", e)
            }
        }
    }

    private inner class MyAdapter constructor(context: Context, list: List<String>) : ArrayAdapter<String>(context, android.R.layout.simple_expandable_list_item_1, android.R.id.text1, list) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = super.getView(position, convertView, parent)
            val id = getItem(position)!!
            val nameView = view.findViewById<TextView>(android.R.id.text1)
            val name = serviceList.firstOrNull { it.first == id }?.second
            if (name != null) nameView.text = name else nameView.text = "Unknown Service"
            return view
        }
    }
}