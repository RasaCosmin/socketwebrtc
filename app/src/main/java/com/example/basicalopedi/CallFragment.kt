package com.example.basicalopedi

import androidx.lifecycle.ViewModelProviders
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import kotlinx.android.synthetic.main.call_fragment.*


class CallFragment : Fragment() {

    companion object {
        fun newInstance() = CallFragment()
    }

    private lateinit var viewModel: QueueViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.call_fragment, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProviders.of(this.requireActivity()).get(QueueViewModel::class.java)

        viewModel.localVideoRender = local_renderer
        viewModel.remoteVideoRender = remote_renderer

        viewModel.attachEvents()

        subscribeToViewModel()

        viewModel.emitJoin()
    }

    private fun subscribeToViewModel() {
        viewModel.showNoInternet.observe(viewLifecycleOwner, Observer {
            it?.run {
                val message = if (this) {
                    "Nu Exista"
                } else {
                    "Exista"
                }

                Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
            }
        })
    }

}
