package com.example.uberfrontend.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.uberfrontend.R
import com.example.uberfrontend.databinding.FragmentChooseRoleBinding
import com.example.uberfrontend.session.SessionManager

class ChooseRoleFragment : Fragment() {

    private var _binding: FragmentChooseRoleBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChooseRoleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        binding.btnUser.setOnClickListener {
            SessionManager.saveRole(requireContext(), "USER")
            findNavController().navigate(
                R.id.action_chooseRoleFragment_to_loginFragment
            )
        }

        binding.btnDriver.setOnClickListener {
            SessionManager.saveRole(requireContext(), "DRIVER")
            findNavController().navigate(
                R.id.action_chooseRoleFragment_to_loginFragment
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
