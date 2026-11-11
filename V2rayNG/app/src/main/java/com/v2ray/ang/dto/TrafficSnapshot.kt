package com.v2ray.ang.dto

import java.io.Serializable

/**
 * Live traffic of the running connection, sent from the :daemon process to the UI every few
 * seconds. Speeds are bytes per second; session totals are bytes since the connection started.
 */
data class TrafficSnapshot(
    val upSpeed: Long = 0,
    val downSpeed: Long = 0,
    val sessionUp: Long = 0,
    val sessionDown: Long = 0,
) : Serializable
