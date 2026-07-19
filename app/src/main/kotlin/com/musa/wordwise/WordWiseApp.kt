// Copyright 2026 Fackson Mutetesha (Musa-dabwe)
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0

package com.musa.wordwise

import android.app.Application
import com.musa.wordwise.server.WwServer

class WordWiseApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Runs before MainActivity creates the WebView so the frontend is
        // (usually) already listening by the time the page is requested.
        WwServer.start(this)
    }
}
