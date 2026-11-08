package com.example.gymtrack.assistant

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** Conectividad: solo Pibble la necesita; el resto de GymTrack funciona sin internet. */
object PibbleNetwork {

    fun isOnline(context: Context): Boolean {
        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

class PibbleNoInternetException : Exception("no_internet")
