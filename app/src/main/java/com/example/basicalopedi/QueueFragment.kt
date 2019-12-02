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


class QueueFragment : Fragment() {

    companion object {
        fun newInstance() = QueueFragment()
    }

    private lateinit var viewModel: QueueViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.queue_fragment, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProviders.of(this.requireActivity()).get(QueueViewModel::class.java)
        viewModel.setContext(activity?.applicationContext!!)

        subscribeToViewModel()
        viewModel.connectToSocket()
    }

    private fun subscribeToViewModel() {
        viewModel.move.observe(viewLifecycleOwner, Observer {
            it?.run {
                if(this){
                    findNavController().navigate(R.id.action_queueFragment_to_callFragment)
                }
            }
        })

        viewModel.showNoInternet.observe(viewLifecycleOwner, Observer {
            it?.run {
                val message = if(this){
                    "Nu Exista"
                }else{
                    "Exista"
                }

                Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
            }
        })
    }

}
