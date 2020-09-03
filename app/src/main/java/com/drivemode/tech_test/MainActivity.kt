package com.drivemode.tech_test

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    lateinit var scanFragment: ScanFragment
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // In order to manage properly fragment replacement ScanFragment is attached to container at start
        // * it will be replaced later to display services in DisplayFragment
        scanFragment = ScanFragment()
        supportFragmentManager
                .beginTransaction()
                .replace(R.id.frameContainer, scanFragment)
                .show(scanFragment)
                .commit()
    }
}