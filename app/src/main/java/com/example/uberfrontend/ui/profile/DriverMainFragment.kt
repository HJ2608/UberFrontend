package com.example.uberfrontend.ui.profile

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.uberfrontend.R
import com.google.android.material.bottomnavigation.BottomNavigationView

class DriverMainFragment : Fragment(R.layout.fragment_driver_main) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val navHost =
            childFragmentManager.findFragmentById(R.id.driverNavHost) as NavHostFragment
        val navController = navHost.navController

        val bottomNav = view.findViewById<BottomNavigationView>(R.id.driverBottomNav)
        bottomNav.setupWithNavController(navController)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            bottomNav.visibility = when (destination.id) {
                R.id.driverCurrentRideFragment,
                R.id.driverPaymentFragment -> View.GONE
                else -> View.VISIBLE
            }
        }
    }

    override fun onResume() {
        super.onResume()
        requireActivity()
            .findViewById<View>(R.id.bottom_nav)
            ?.visibility = View.GONE
    }

    override fun onPause() {
        super.onPause()
        requireActivity()
            .findViewById<View>(R.id.bottom_nav)
            ?.visibility = View.VISIBLE
    }

}
