package com.example.uberfrontend.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.uberfrontend.R
import com.example.uberfrontend.databinding.FragmentProfileBinding
import com.example.uberfrontend.data.session.SessionManager

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val fullName = listOf(SessionManager.firstName, SessionManager.lastName)
            .filter { !it.isNullOrBlank() }
            .joinToString(" ")
            .ifBlank { "--" }

        binding.tvName.text = "Name: $fullName"
        binding.tvMobile.text = "Mobile: ${SessionManager.mobile ?: "--"}"
        binding.tvEmail.text = "Email: ${SessionManager.email ?: "--"}"

        binding.btnLogout.setOnClickListener {
            SessionManager.clear(requireContext())

            findNavController().navigate(
                R.id.loginFragment,
                null,
                androidx.navigation.NavOptions.Builder()
                    .setPopUpTo(R.id.nav_graph, true)
                    .build()
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
