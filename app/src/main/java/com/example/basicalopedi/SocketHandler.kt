package com.example.basicalopedi

import android.util.Log
import com.google.gson.GsonBuilder
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter
import org.json.JSONObject
import javax.inject.Inject


/**
 * Created by Rasa Cosmin on 12/08/2019.
 */
class SocketHandler @Inject constructor() {
    private val socket: Socket by lazy { createSocketIO() }
    private val gson = GsonBuilder().serializeNulls().create()

    private fun createSocketIO(): Socket {
        /*val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
             override fun getAcceptedIssuers(): Array<X509Certificate> {
                 return arrayOf()
             }


             @Throws(CertificateException::class)
             override fun checkClientTrusted(
                 chain: Array<X509Certificate>,
                 authType: String
             ) {
             }

             @Throws(CertificateException::class)
             override fun checkServerTrusted(
                 chain: Array<X509Certificate>,
                 authType: String
             ) {
             }
         })

         val myHostnameVerifier = HostnameVerifier { _, _ -> true }

         var mySSLContext: SSLContext? = null
         try {
             mySSLContext = SSLContext.getInstance("TLS")
             try {
                 mySSLContext!!.init(null, trustAllCerts, null)
             } catch (e: KeyManagementException) {
                 e.printStackTrace()
             }

         } catch (e: NoSuchAlgorithmException) {
             e.printStackTrace()
         }

         val okHttpClient = OkHttpClient.Builder().hostnameVerifier(myHostnameVerifier)
             .sslSocketFactory(mySSLContext!!.socketFactory).build()

         // default settings for all sockets
         IO.setDefaultOkHttpWebSocketFactory(okHttpClient)
         IO.setDefaultOkHttpCallFactory(okHttpClient)*/

        val options = IO.Options()
        options.forceNew = true
        options.reconnection = true
        options.reconnectionDelay = 1000
        options.reconnectionAttempts = 9999/*
        options.callFactory = okHttpClient
        options.webSocketFactory = okHttpClient*/
        val socket = IO.socket("https://helpdesk-dev.hypertalk.net:443", options)
        Log.d("test", "Socket id = ${socket.id()}")

        socket.on(Socket.EVENT_ERROR) {
            Log.d("error", "error + event_error")
        }

        socket.on(Socket.EVENT_CONNECT_ERROR) {
            Log.d("error", "error + event error connect ${it[0]}")
        }

        return socket
    }

    fun connect() {
        if (socket.connected()) {
            socket.disconnect()
        }

        socket.connect()
    }

    fun attachEvent(eventName: String, listener: Emitter.Listener) {
        if(socket.listeners(eventName).size>0){
            detachEvent(eventName)
        }
        socket.on(eventName, listener)
    }

    fun detachEvent(eventName: String) {
        socket.off(eventName)
    }

    fun emitMessage(eventName: String, args: Any?) {
        val convertedArgs = gson.toJson(args)
        Log.d("test", "$eventName $convertedArgs")
        socket.emit(eventName, JSONObject(convertedArgs))
    }

    /*private fun emitMessage(eventName: String, args: String) {
        Log.d("test", "$eventName $args")

        val jsonObject = JSONObject(args)
        if (addUserAgent) {
            jsonObject.put("userAgent", JSONObject(gson.toJson(UserAgent())))
        }

        socket.emit(eventName, jsonObject)
    }*/

    fun closeSocket() {
        if (socket.connected()) {
            Log.d("test", "Socket disconnect")
            socket.disconnect()
            socket.off()
        }
    }

}