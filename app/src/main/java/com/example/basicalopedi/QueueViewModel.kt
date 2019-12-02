package com.example.basicalopedi

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.basicalopedi.webrtc.CustomPeer
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import io.socket.client.Socket
import io.socket.emitter.Emitter
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription
import org.webrtc.SurfaceViewRenderer
import java.io.Serializable

class QueueViewModel : ViewModel() {
    private val socketHandler: SocketHandler by lazy { SocketHandler() }
    lateinit var session: Session


    private lateinit var context: Context

    fun setContext(context: Context) {
        this.context = context
    }

    lateinit var localVideoRender: SurfaceViewRenderer
    lateinit var remoteVideoRender: SurfaceViewRenderer


    val move: LiveData<Boolean>
        get() = _move
    private val _move = MutableLiveData<Boolean>(false)

    val showNoInternet: LiveData<Boolean>
        get() = _showNoInternet

    private val _showNoInternet = MutableLiveData<Boolean>()

    private val activePresenters = ArrayList<Session>()

    private val myIds = ArrayList<String>()

    private val peers = HashMap<String, CustomPeer>()

    fun connectToSocket() {
        attachRegEvents()
        socketHandler.connect()
    }

    private fun attachRegEvents() {
        socketHandler.attachEvent("userRegistered", onUserRegistered)
        socketHandler.attachEvent(Socket.EVENT_CONNECT, onConnect)
    }

    private fun attachQEvents() {
        socketHandler.attachEvent("pickedUser", onPickedUser)
        socketHandler.attachEvent("userRegistered", onUserRegistered)

        socketHandler.attachEvent(Socket.EVENT_RECONNECT, onReconnect)
        socketHandler.attachEvent(Socket.EVENT_DISCONNECT, onDisconnect)

    }

    private val onConnect = Emitter.Listener {
        Log.d("test", "onConnect")



        socketHandler.emitMessage("joinQueue", constructJoinData())
    }

    private val onUserRegistered = Emitter.Listener {
        socketHandler.detachEvent("userRegistered")

        session = Gson().fromJson(it[0].toString(), Session::class.java)

        attachQEvents()
    }

    private val onPickedUser = Emitter.Listener {
        val pickedUser = Gson().fromJson(it[0].toString(), Session::class.java)
        if (session.id == pickedUser.id) {
            viewModelScope.launch {
                _move.value = true
            }
        }
    }

    private fun constructJoinData(): JoinData {
        val user = JoinUser("5da41c07ef30d600106d152c")

        val joinQueModel = JoinQueueModel(user)

        return JoinData(joinQueModel, HTData("Rasa Cosmin", "0766204366"))
    }

    private val onReconnect = Emitter.Listener {
        viewModelScope.launch {
            _showNoInternet.value = false
        }
    }
    private val onDisconnect = Emitter.Listener {
        viewModelScope.launch {
            _showNoInternet.value = true
        }
    }


    fun attachEvents() {

        socketHandler.attachEvent("joined-iOS", Emitter.Listener {
            Log.d("test", "joined-iOS ${it[0]}")
            viewModelScope.launch {
                val joinedObject = JSONObject(it[0].toString())

                if (joinedObject.has("user")) {

                    val oldSession = Gson().toJson(session)
                    val oldSessionJson = JSONObject(oldSession)

                    val newSessionObj = joinedObject["user"] as JSONObject

                    for (key in newSessionObj.keys()) {
                        if (oldSessionJson.has(key)) {
                            oldSessionJson.put(key, newSessionObj[key])
                        }
                    }

                    val newSession = Gson().fromJson(
                        oldSessionJson.toString(),
                        Session::class.java
                    )

                    session = newSession

                    myIds.add(newSession.id!!)
                    //socketHandler.emitMessage("activatePresenter", sessionLiveData.value)
                }


                activePresenters.clear()

                if (joinedObject.has("activePresenters")) {
                    activePresenters.addAll(
                        Gson().fromJson<ArrayList<Session>>(
                            joinedObject.getJSONArray("activePresenters").toString(),
                            object : TypeToken<ArrayList<Session>>() {}.type
                        )
                    )
                }

                startVideoCall(session.id!!)
            }
        })

        socketHandler.attachEvent("user-list", Emitter.Listener {
            Log.d("test", "user-list ${it[0]}")
        })
        socketHandler.attachEvent("user-out-iOS", Emitter.Listener {
            Log.d("test", "user-out-iOS ${it[0]}")
        })
        socketHandler.attachEvent("roomUnavailable-iOS", Emitter.Listener {
            Log.d("test", "roomUnavailable-iOS ${it[0]}")
        })
        socketHandler.attachEvent("sessionPaused-iOS", Emitter.Listener {
            Log.d("test", "sessionPaused-iOS ${it[0]}")
        })
        socketHandler.attachEvent("changePeerStatus", Emitter.Listener {
            Log.d("test", "changePeerStatus ${it[0]}")
        })
        socketHandler.attachEvent("sessionRestarted-iOS", Emitter.Listener {
            Log.d("test", "sessionRestarted-iOS ${it[0]}")
        })
        socketHandler.attachEvent("iceCandidate-iOS", Emitter.Listener {
            Log.d("test", "iceCandidate-iOS ${it[0]}")

            val iceCandidateJson = JSONObject(it[0].toString())

            val candidate = iceCandidateJson.getJSONObject("candidate")
            val candidateStr = candidate.getString("candidate")
            val id = iceCandidateJson.getString("idPresenter")
            val sdpMid = candidate.getString("sdpMid")
            val sdpMLineIndex = candidate.getInt("sdpMLineIndex")
            val iceCandidate = IceCandidate(sdpMid, sdpMLineIndex, candidateStr)
            peers[id]?.addRemoteIceCandidate(iceCandidate)

        })
        socketHandler.attachEvent("iceCandidateTest-iOS", Emitter.Listener {
            Log.d("test", "iceCandidateTest-iOS ${it[0]}")
        })
        socketHandler.attachEvent("sdpAnswerFromServerTest-iOS", Emitter.Listener {
            Log.d("test", "sdpAnswerFromServerTest-iOS ${it[0]}")
        })
        socketHandler.attachEvent("sdpAnswerFromServer-iOS", Emitter.Listener {
            Log.d("test", "sdpAnswerFromServer-iOS ${it[0]}")
            val sdpAnswer = JSONObject(it[0].toString())
            val sd = SessionDescription(
                SessionDescription.Type.ANSWER,
                sdpAnswer["sdpAnswer"].toString()
            )
            val id = sdpAnswer.getString("id")

            peers[id]?.setRemoteDescription(sd)
        })
        socketHandler.attachEvent("deactivatePresenter-iOS", Emitter.Listener {
            Log.d("test", "deactivatePresenter-iOS ${it[0]}")
        })
        socketHandler.attachEvent("notifyNewScreenShare-iOS", Emitter.Listener {
            Log.d("test", "notifyNewScreenShare-iOS ${it[0]}")
        })
        socketHandler.attachEvent("activeScreenSharePresenters-iOS", Emitter.Listener {
            Log.d("test", "activeScreenSharePresenters-iOS ${it[0]}")
        })
        socketHandler.attachEvent("deactivateScreenSharePresenter-iOS", Emitter.Listener {
            Log.d("test", "deactivateScreenSharePresenter-iOS ${it[0]}")
        })
        socketHandler.attachEvent("newPresenterAvailableWithUsersInRoom-iOS", Emitter.Listener {
            Log.d("test", "newPresenterAvailableWithUsersInRoom-iOS ${it[0]}")
        })
        socketHandler.attachEvent("newPresenterAvailable-iOS", Emitter.Listener {
            Log.d("test", "newPresenterAvailable-iOS ${it[0]}")

            val newPresenter = JSONObject(it[0].toString())
            viewModelScope.launch {
                Log.d("RTCclient", "newPresenterAvailable-iOS")
                startViewer(newPresenter.getString("id"))
            }

        })

        socketHandler.attachEvent("chat-messages-list-iOS", Emitter.Listener {
            Log.d("test", "chat-messages-list-iOS ${it[0]}")
        })
        socketHandler.attachEvent("chagePeerMediaSettings-iOS", Emitter.Listener {
            Log.d("test", "chagePeerMediaSettings-iOS ${it[0]}")

        })
        socketHandler.attachEvent("userChangedStatus-iOS", Emitter.Listener {
            Log.d("test", "userChangedStatus-iOS ${it[0]}")
        })
        socketHandler.attachEvent("operatorEndedCall", Emitter.Listener {
            Log.d("test", "operatorEndedCall ${it[0]}")

            val endObj = JSONObject(it[0].toString())
            if (session.sessionId == endObj["sessionId"])
                endCall()
        })

        socketHandler.attachEvent("created", Emitter.Listener {
            Log.d("test", "created ${it[0]}")
            viewModelScope.launch { startVideoCall(session.id!!) }
        })
        socketHandler.attachEvent("user-list-iOS", Emitter.Listener {
            Log.d("test", "user-list-iOS ${it[0]}")
        })
        socketHandler.attachEvent("new-user", Emitter.Listener {
            Log.d("test", "new-user ${it[0]}")
        })
        socketHandler.attachEvent("create-moderator-with-users-inroom", Emitter.Listener {
            Log.d("test", "create-moderator-with-users-inroom ${it[0]}")
        })
        socketHandler.attachEvent("userChangedStatus", Emitter.Listener {
            Log.d("test", "userChangedStatus ${it[0]}")

            val peerSettings = JSONObject(it[0].toString())

            /*setCameraParams(
                peerSettings.getString("audio"),
                peerSettings.getString("camera")
            )*/

            peers[session.id]?.setCamera()


            //mediaResourcesStatus(session.id!!)
            //socketHandler.emitMessage("updateUserStatus", session)

        })
        socketHandler.attachEvent("user-out", Emitter.Listener {
            Log.d("test", "user-out ${it[0]}")
        })

        socketHandler.attachEvent(Socket.EVENT_CONNECT, Emitter.Listener {
            Log.d("test", "EVENT_CONNECT")
            emitJoin()
            //emitGetMessages()
        })

        socketHandler.attachEvent(Socket.EVENT_RECONNECT, Emitter.Listener {
            Log.d("test", "EVENT_RECONNECT ${it[0]}")
            // emitJoin()
            //emitJoinQueueRoomReconnect()
            viewModelScope.launch {
                _showNoInternet.value = false
            }

            //rtcClient.closeLocalConnection(sessionLiveData.value!!.id!!)
            peers.forEach { (k, v) -> v.close() }

            /*remoteView.clearImage()
            remoteView.release()
            localView.clearImage()
            localView.release()*/
        })

        socketHandler.attachEvent(Socket.EVENT_DISCONNECT, Emitter.Listener {
            Log.d("test", "EVENT_DISCONNECT")
            //rtcClient.closeLocalConnection(sessionLiveData.value!!.id!!)
            //rtcClient.closeConnection()
            //releaseRenderers()

            viewModelScope.launch {
                _showNoInternet.value = true
            }

        })
    }

     fun emitJoin() {
        socketHandler.emitMessage("create or join", session)
    }

    private fun endCall() {
        peers.forEach { (k, v) -> v.close() }
        activePresenters.clear()
        myIds.clear()

        socketHandler.closeSocket()
    }

    private fun startVideoCall(connectionId: String) {
        val peer = CustomPeer(context, object : SocketEvents {
            override fun setLocalSDP(localSDP: SessionDescription) {
                session.sdpOffer = localSDP.description

                val event = if (activePresenters.isNotEmpty()) {
                    "startPresenterWhenUsersExistInRoom"
                } else {
                    "startPresenter"
                }
                socketHandler.emitMessage(event, session)

                if (activePresenters.isNotEmpty()) {
                    connectToActivePresenters()
                }

            }

            override fun onIceCandidate(iceCandidate: IceCandidate) {
                val cand = Candidate(
                    iceCandidate.sdp,
                    iceCandidate.sdpMid,
                    iceCandidate.sdpMLineIndex
                )

                val candidate =
                    IceCandidateModel(cand, session.id, connectionId)

                socketHandler.emitMessage("onIceCandidate", candidate)
            }

        }, localVideoRender, remoteVideoRender)

        peer.initLocalRenderer()

        peers[connectionId] = peer
    }

    private fun startViewer(connectionId: String) {

        val peer = CustomPeer(context, object : SocketEvents {
            override fun setLocalSDP(localSDP: SessionDescription) {
                session.sdpOffer = localSDP.description
                val viewer = Viewer(connectionId, session)
                socketHandler.emitMessage("startViewer", viewer)

            }

            override fun onIceCandidate(iceCandidate: IceCandidate) {
                val cand = Candidate(
                    iceCandidate.sdp,
                    iceCandidate.sdpMid,
                    iceCandidate.sdpMLineIndex
                )

                val candidate =
                    IceCandidateModel(cand, session.id, connectionId)

                socketHandler.emitMessage("onIceCandidate", candidate)
            }
        }, localVideoRender, remoteVideoRender)
        peer.initRemoteRenderer()

        peers[connectionId] = peer
    }

    private fun connectToActivePresenters() {
        viewModelScope.launch {
            for (presenter in activePresenters) {
                Log.d("RTCclient", "connectToActivePresenters ${presenter.id}")

                if (!myIds.contains(presenter.id!!))

                    startViewer(presenter.id)
            }
        }
    }
}

interface SocketEvents {
    fun setLocalSDP(localSDP: SessionDescription)

    fun onIceCandidate(iceCandidate: IceCandidate)
}

data class JoinUser(
    @field:SerializedName("idUser")
    var idUser: String,
    @field:SerializedName("idFamily")
    var idFamily: String? = null
) : Serializable

data class JoinChild(
    @field:SerializedName("idChild")
    var idChild: String? = null,
    @field:SerializedName("extra")
    var extra: ExtraChild? = null
) : Serializable

data class ExtraChild(
    @field:SerializedName("name")
    var name: String? = null,
    @field:SerializedName("allergies")
    var allergies: String? = null,
    @field:SerializedName("dateOfBirth")
    var dateOfBirth: String? = null,
    @field:SerializedName("weight")
    var weight: String? = null,
    @field:SerializedName("location")
    var location: String? = null
) : Serializable

data class JoinQueueModel(
    @field:SerializedName("user")
    val user: JoinUser? = null,
    @field:SerializedName("child")
    val child: JoinChild? = null,
    @field:SerializedName("type")
    var type: Int = 0
) : Serializable

data class JoinData(
    @field:SerializedName("data")
    val data: JoinQueueModel? = null,

    @field:SerializedName("htData")
    val htData: HTData? = null
)

data class HTData(
    @field:SerializedName("name")
    val name: String?,
    @field:SerializedName("email")
    val phone: String?
)

data class Session(

    @field:SerializedName("clientId")
    val clientId: Int? = null,

    @field:SerializedName("role")
    val role: String? = null,

    @field:SerializedName("currentStatus")
    val currentStatus: String? = null,

    @field:SerializedName("recording")
    val recording: Boolean? = null,

    @field:SerializedName("sessionId")
    val sessionId: String? = null,

    @field:SerializedName("priority")
    val priority: Any? = null,

    @field:SerializedName("type")
    val type: String? = null,

    @field:SerializedName("room")
    val room: String? = null,

    @field:SerializedName("roomId")
    val roomId: String? = null,

    @field:SerializedName("phone")
    val phone: String? = null,

    @field:SerializedName("joinedTime")
    val joinedTime: String? = null,

    @field:SerializedName("name")
    var name: String? = null,

    @field:SerializedName("id")
    val id: String? = null,

    @field:SerializedName("audio")
    var audio: String? = null,

    @field:SerializedName("camera")
    var camera: String? = null,

    @field:SerializedName("numberOfAllowedPanelists")
    val numberOfAllowedPanelists: Int? = null,

    @field:SerializedName("scheduledAt")
    val scheduledAt: Any? = null,

    @field:SerializedName("email")
    val email: String? = null,

    @field:SerializedName("sdpOffer")
    var sdpOffer: String? = null,

    @field:SerializedName("cameraControl")
    var cameraControl: Int? = 0,

    @field:SerializedName("micControl")
    var micControl: Int? = 0,

    @field:SerializedName("deviceType")
    val deviceType: String = "mobile",

    @field:SerializedName("userAgent")
    val userAgent: UserAgent = UserAgent(),

    @field:SerializedName("data")
    val data: JoinQueueModel? = null
) : Serializable

data class UserAgent(
    val deviceModel: String = Build.MODEL,
    val os: String = "Android: ${Build.VERSION.RELEASE}"
) : Serializable

data class IceCandidateModel(
    @field:SerializedName("candidate")
    val candidate: Candidate? = null,

    @field:SerializedName("sender")
    val sender: String? = null,

    @field:SerializedName("presenter")
    val presenter: String? = null,

    @field:SerializedName("type")
    val type: String? = "video"
)

data class Candidate(
    @field:SerializedName("candidate")
    val candidate: String? = null,

    @field:SerializedName("sdpMid")
    val sdpMid: String? = null,

    @field:SerializedName("sdpMLineIndex")
    val sdpMLineIndex: Int? = null
)

data class Viewer(
    @field:SerializedName("presenterId")
    var presenterId: String? = null,

    @field:SerializedName("user")
    var user: Session
)