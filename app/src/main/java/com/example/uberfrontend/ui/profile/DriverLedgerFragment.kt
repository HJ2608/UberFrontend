package com.example.uberfrontend.ui.profile

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.example.uberfrontend.R
import com.example.uberfrontend.data.session.SessionManager
import androidx.navigation.findNavController

class DriverLedgerFragment : Fragment(R.layout.fragment_driver_ledger) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setHasOptionsMenu(true)

        // load earnings here...
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_ledger, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_logout -> {
                SessionManager.clear(requireContext())
                Toast.makeText(requireContext(), "Logged out", Toast.LENGTH_SHORT).show()

                val nav = requireActivity().findNavController(R.id.nav_host_fragment)

                nav.navigate(
                    R.id.loginFragment,
                    null,
                    NavOptions.Builder()
                        .setPopUpTo(R.id.nav_graph, true)
                        .build()
                )

                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
