package com.example.basicalopedi.webrtc

import android.content.Context
import com.example.basicalopedi.SocketEvents
import org.webrtc.*
import org.webrtc.MediaConstraints.KeyValuePair
import java.lang.Exception


/**
 * Created by Rasa Cosmin on 02/12/2019.
 */

class CustomPeer(
    private val context: Context,
    private val socketEvents: SocketEvents,
    private val localVideoRender: SurfaceViewRenderer,
    private val remoteVideoRender: SurfaceViewRenderer
) {

    companion object {
        const val VIDEO_TRACK_ID = "ARDAMSv0"
        const val AUDIO_TRACK_ID = "ARDAMSa0"
    }

    private var isInitiator = false
    private val rootEglBase = EglBase.create()

    private val sdpObserver = SDPObserver()
    private lateinit var localSDP: SessionDescription

    private lateinit var localAudioTrack: AudioTrack
    private lateinit var localVideoTrack: VideoTrack

    private val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:turn01.hypertalk.net:80").createIceServer(),
        PeerConnection.IceServer.builder("turn:turn01.hypertalk.net:80?transport=tcp")
            .setUsername("tudor")
            .setPassword("parola")
            .createIceServer()
    )

    private val peerConnectionFactory by lazy { createPeerConnectionFactory() }
    private val videoCapturer by lazy { createCameraCapturer() }
    private var peer: PeerConnection? = null

    private fun createPeerConnectionFactory(): PeerConnectionFactory {
        val initializationOptions = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initializationOptions)

        val options = PeerConnectionFactory.Options()

        return PeerConnectionFactory.builder()
            .setOptions(options)
            .setVideoEncoderFactory(SoftwareVideoEncoderFactory())
            .setVideoDecoderFactory(SoftwareVideoDecoderFactory())
            .createPeerConnectionFactory()
    }


    private fun createCameraCapturer(): VideoCapturer? {

        val cameraEnumerator =
            if (Camera2Enumerator.isSupported(context))
                Camera2Enumerator(context)
            else Camera1Enumerator(false)

        return cameraEnumerator.run {
            deviceNames.find {
                isFrontFacing(it)
            }?.let {
                createCapturer(it, null)
            } ?: deviceNames.find {
                !isFrontFacing(it)
            }?.let {
                createCapturer(it, null)
            }
        }
    }

    fun createPeerConnection() {
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED
            bundlePolicy = PeerConnection.BundlePolicy.BALANCED
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.NEGOTIATE
        }

        peer = peerConnectionFactory.createPeerConnection(rtcConfig, PCObserver())
    }

    fun creatPcConstrains() = MediaConstraints().apply {
        optional.add(KeyValuePair("DtlsSrtpKeyAgreement", "true"))
        optional.add(KeyValuePair("RtpDataChannels", "true"))
        optional.add(
            KeyValuePair(
                "internalSctpDataChannels",
                "true"
            )
        )
    }

    fun creatvideoConstraints(): MediaConstraints {
        val videoConstraints = MediaConstraints()


        val MAX_VIDEO_WIDTH_CONSTRAINT = "maxWidth"
        val MIN_VIDEO_WIDTH_CONSTRAINT = "minWidth"
        val MAX_VIDEO_HEIGHT_CONSTRAINT = "maxHeight"
        val MIN_VIDEO_HEIGHT_CONSTRAINT = "minHeight"
        val MAX_VIDEO_FPS_CONSTRAINT = "maxFrameRate"
        val MIN_VIDEO_FPS_CONSTRAINT = "minFrameRate"
        var videoWidth = 0
        var videoHeight = 0
        if ((videoWidth == 0 || videoHeight == 0) && MediaCodecVideoEncoder.isVp8HwSupported()) {
            videoWidth = 1280
            videoHeight = 1280
        }
        if (videoWidth > 0 && videoHeight > 0) {
            videoWidth = Math.min(videoWidth, 1280)
            videoHeight = Math.min(videoHeight, 1280)
            videoConstraints.mandatory.add(
                KeyValuePair(
                    MIN_VIDEO_WIDTH_CONSTRAINT,
                    videoWidth.toString()
                )
            )
            videoConstraints.mandatory.add(
                KeyValuePair(
                    MAX_VIDEO_WIDTH_CONSTRAINT,
                    videoWidth.toString()
                )
            )
            videoConstraints.mandatory.add(
                KeyValuePair(
                    MIN_VIDEO_HEIGHT_CONSTRAINT,
                    videoHeight.toString()
                )
            )
            videoConstraints.mandatory.add(
                KeyValuePair(
                    MAX_VIDEO_HEIGHT_CONSTRAINT,
                    videoHeight.toString()
                )
            )
        }
        val videoFps = 30
        videoConstraints.mandatory.add(
            KeyValuePair(
                MIN_VIDEO_FPS_CONSTRAINT,
                videoFps.toString()
            )
        )
        videoConstraints.mandatory.add(
            KeyValuePair(
                MAX_VIDEO_FPS_CONSTRAINT,
                videoFps.toString()
            )
        )

        return videoConstraints
    }

    fun creataudioConstraints() = MediaConstraints().apply {
        optional.add(KeyValuePair("DtlsSrtpKeyAgreement", "true"))
        optional.add(KeyValuePair("RtpDataChannels", "true"))
        optional.add(
            KeyValuePair(
                "internalSctpDataChannels",
                "true"
            )
        )
    }

    fun creatsdpMediaConstraints() = MediaConstraints().apply {
        mandatory.add(KeyValuePair("OfferToReceiveAudio", "true"))
        mandatory.add(KeyValuePair("OfferToReceiveVideo", "true"))
    }

    fun initSurfaceView(view: SurfaceViewRenderer) = view.run {
        setMirror(false)
        setEnableHardwareScaler(true)
        init(rootEglBase.eglBaseContext, null)
    }

    fun initLocalRenderer() {
        try {
            localVideoRender.run {
                setMirror(false)
                setEnableHardwareScaler(true)
                init(rootEglBase.eglBaseContext, null)
            }
        } catch (ex: Exception) {

        }
        startLocalVideoCapture()
    }

    fun initRemoteRenderer(){
        try {
            remoteVideoRender.run {
                setMirror(false)
                setEnableHardwareScaler(true)
                init(rootEglBase.eglBaseContext, null)
            }
        } catch (ex: Exception) {

        }
    }



    private fun startLocalVideoCapture() {
        val mediaStream = peerConnectionFactory.createLocalMediaStream("ARDAMS")

        val videoSource =
            peerConnectionFactory.createVideoSource(videoCapturer?.isScreencast ?: false)


        localVideoTrack = peerConnectionFactory.createVideoTrack(VIDEO_TRACK_ID, videoSource)
        localVideoTrack.addSink(localVideoRender)
        val audioSource = peerConnectionFactory.createAudioSource(creataudioConstraints())
        localAudioTrack = peerConnectionFactory.createAudioTrack(AUDIO_TRACK_ID, audioSource)
        mediaStream.addTrack(localAudioTrack)
        mediaStream.addTrack(localVideoTrack)

        peer?.addStream(mediaStream)
    }


    fun createOffer() {
        peer?.run {
            isInitiator = true
            createOffer(sdpObserver, creatsdpMediaConstraints())
        }
    }

    fun createAnswer() {
        peer?.run {
            isInitiator = false
            createAnswer(sdpObserver, creatsdpMediaConstraints())
        }
    }

    fun addRemoteIceCandidate(iceCandidate: IceCandidate) {
        peer?.addIceCandidate(iceCandidate)
    }

    fun setLocalDescipton(sdp: SessionDescription) {
        peer?.setLocalDescription(sdpObserver, sdp)
    }

    fun setRemoteDescription(sdp: SessionDescription) {
        peer?.setRemoteDescription(sdpObserver, sdp)
    }

    fun close() {
        peer?.close()
    }

    fun setCamera() {
        localVideoTrack.setEnabled(true)
        localAudioTrack.setEnabled(true)
    }

    inner class SDPObserver : SdpObserver {
        override fun onSetFailure(p0: String?) {
        }

        override fun onSetSuccess() {
            socketEvents.setLocalSDP(localSDP)
        }

        override fun onCreateSuccess(sdp: SessionDescription) {
            localSDP = sdp
            setLocalDescipton(sdp)
        }

        override fun onCreateFailure(p0: String?) {
        }

    }

    inner class PCObserver : PeerConnection.Observer {
        override fun onIceCandidate(iceCandidate: IceCandidate) {
            socketEvents.onIceCandidate(iceCandidate)
        }

        override fun onDataChannel(p0: DataChannel?) {
        }

        override fun onIceConnectionReceivingChange(p0: Boolean) {
        }

        override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {
        }

        override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {
        }

        override fun onAddStream(mediaStream: MediaStream) {
            peer?.run {
                if (mediaStream.audioTracks.size > 1 || mediaStream.videoTracks.size > 1) {
                    return
                }
                if (mediaStream.videoTracks.size == 1) {
                    val videoTrack = mediaStream.videoTracks[0]
                    videoTrack.setEnabled(true)
                    videoTrack.addSink(remoteVideoRender)
                }
            }
        }

        override fun onSignalingChange(p0: PeerConnection.SignalingState?) {
        }

        override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {
        }

        override fun onRemoveStream(p0: MediaStream?) {
        }

        override fun onRenegotiationNeeded() {
        }

        override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {
        }
    }
}