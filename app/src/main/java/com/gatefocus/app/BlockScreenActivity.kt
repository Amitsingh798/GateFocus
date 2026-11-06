package com.gatefocus.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class BlockScreenActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = intent.getStringExtra("blocked_app") ?: "Distracting content"
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 80, 40, 40)
            setBackgroundColor(0xFF10182D.toInt())
        }
        layout.addView(TextView(this).apply {
            text = "Stay on your GATE goal"
            textSize = 28f
            setTextColor(0xFFFFFFFF.toInt())
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        layout.addView(TextView(this).apply {
            text = "Study mode is active.\n$app is blocked for this session.\n\nTake a breath and return to your CSE preparation."
            textSize = 17f
            setTextColor(0xFFD7DDF0.toInt())
            setPadding(0, 24, 0, 32)
        })
        layout.addView(Button(this).apply {
            text = "Back to GATE FOCUS"
            setOnClickListener {
                startActivity(
                    Intent(this@BlockScreenActivity, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                )
                finish()
            }
        })
        setContentView(layout)
    }
}
