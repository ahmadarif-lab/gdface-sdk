package com.ahmadarif.gdface.sample

import android.app.Application
import android.content.Context

class GdFaceApp : Application() {

    lateinit var settings: AppSettings
        private set
    lateinit var service: FaceService
        private set
    lateinit var faces: FaceRepository
        private set

    override fun onCreate() {
        super.onCreate()
        settings = AppSettings(this)
        service = FaceService(this, settings)
        faces = FaceRepository(this, service)
        service.start()
    }
}

val Context.gdApp: GdFaceApp get() = applicationContext as GdFaceApp
