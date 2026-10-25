package com.klannato.bmtron

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import kotlin.math.abs

class MainActivity : Activity() {
    private lateinit var gameView: GameView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        gameView = GameView(this)
        setContentView(gameView)
    }

    override fun onPause() {
        super.onPause()
        gameView.pause()
    }

    override fun onResume() {
        super.onResume()
        gameView.resume()
    }
}

class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback, Runnable {
    private val holder: SurfaceHolder = getHolder()
    private var thread: Thread? = null
    private var running = false

    // Screen dimensions
    private var screenWidth = 0
    private var screenHeight = 0

    // Game state
    private var gameState = STATE_PLAYING
    private var score1 = 0
    private var score2 = 0

    // Player 1 (top) - starts at top, moves down
    private var p1x = 0f
    private var p1y = 0f
    private var p1dx = 0f
    private var p1dy = 1f  // moving down
    private val p1Trail = mutableListOf<Pair<Float, Float>>()
    private var p1Alive = true

    // Player 2 (bottom) - starts at bottom, moves up
    private var p2x = 0f
    private var p2y = 0f
    private var p2dx = 0f
    private var p2dy = -1f  // moving up
    private val p2Trail = mutableListOf<Pair<Float, Float>>()
    private var p2Alive = true

    // Grid
    private val cellSize = 8f
    private val trailWidth = 4f

    // Paints
    private val p1Paint = Paint().apply { color = Color.CYAN; style = Paint.Style.FILL; isAntiAlias = true }
    private val p2Paint = Paint().apply { color = Color.MAGENTA; style = Paint.Style.FILL; isAntiAlias = true }
    private val bgPaint = Paint().apply { color = Color.BLACK }
    private val textPaint = Paint().apply { color = Color.WHITE; textSize = 48f; isAntiAlias = true }
    private val dividerPaint = Paint().apply { color = Color.DKGRAY; strokeWidth = 2f }

    // Touch tracking
    private var p1TouchX = 0f
    private var p2TouchX = 0f

    companion object {
        const val STATE_PLAYING = 0
        const val STATE_GAME_OVER = 1
        const val SPEED = 3f
    }

    init {
        holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        screenWidth = width
        screenHeight = height
        resetGame()
        running = true
        thread = Thread(this)
        thread?.start()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        screenWidth = width
        screenHeight = height
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        running = false
        thread?.join()
    }

    private fun resetGame() {
        p1x = screenWidth / 2f
        p1y = screenHeight * 0.1f
        p1dx = 0f
        p1dy = 1f
        p1Trail.clear()
        p1Alive = true

        p2x = screenWidth / 2f
        p2y = screenHeight * 0.9f
        p2dx = 0f
        p2dy = -1f
        p2Trail.clear()
        p2Alive = true

        gameState = STATE_PLAYING
    }

    override fun run() {
        while (running) {
            if (gameState == STATE_PLAYING) {
                update()
            }
            draw()
            try {
                Thread.sleep(16) // ~60fps
            } catch (e: InterruptedException) {
                // ignore
            }
        }
    }

    private fun update() {
        // Player 1 movement
        if (p1Alive) {
            p1Trail.add(Pair(p1x, p1y))
            p1x += p1dx * SPEED
            p1y += p1dy * SPEED

            // Check wall collision
            if (p1x < 0 || p1x > screenWidth || p1y < 0 || p1y > screenHeight) {
                p1Alive = false
            }

            // Check self collision (trail)
            for (i in 0 until p1Trail.size - 10) {
                val (tx, ty) = p1Trail[i]
                if (abs(p1x - tx) < trailWidth && abs(p1y - ty) < trailWidth) {
                    p1Alive = false
                    break
                }
            }

            // Check opponent trail collision
            for ((tx, ty) in p2Trail) {
                if (abs(p1x - tx) < trailWidth && abs(p1y - ty) < trailWidth) {
                    p1Alive = false
                    break
                }
            }
        }

        // Player 2 movement
        if (p2Alive) {
            p2Trail.add(Pair(p2x, p2y))
            p2x += p2dx * SPEED
            p2y += p2dy * SPEED

            if (p2x < 0 || p2x > screenWidth || p2y < 0 || p2y > screenHeight) {
                p2Alive = false
            }

            for (i in 0 until p2Trail.size - 10) {
                val (tx, ty) = p2Trail[i]
                if (abs(p2x - tx) < trailWidth && abs(p2y - ty) < trailWidth) {
                    p2Alive = false
                    break
                }
            }

            for ((tx, ty) in p1Trail) {
                if (abs(p2x - tx) < trailWidth && abs(p2y - ty) < trailWidth) {
                    p2Alive = false
                    break
                }
            }
        }

        // Check game over
        if (!p1Alive || !p2Alive) {
            if (!p1Alive && !p2Alive) {
                // Draw
            } else if (!p1Alive) {
                score2++
            } else {
                score1++
            }
            gameState = STATE_GAME_OVER
        }
    }

    private fun draw() {
        val canvas = holder.lockCanvas() ?: return
        canvas.drawPaint(bgPaint)

        // Draw divider line
        canvas.drawLine(0f, screenHeight / 2f, screenWidth.toFloat(), screenHeight / 2f, dividerPaint)

        // Draw P1 trail
        for (i in 1 until p1Trail.size) {
            val (x1, y1) = p1Trail[i - 1]
            val (x2, y2) = p1Trail[i]
            canvas.drawLine(x1, y1, x2, y2, p1Paint.apply { strokeWidth = trailWidth })
        }

        // Draw P2 trail
        for (i in 1 until p2Trail.size) {
            val (x1, y1) = p2Trail[i - 1]
            val (x2, y2) = p2Trail[i]
            canvas.drawLine(x1, y1, x2, y2, p2Paint.apply { strokeWidth = trailWidth })
        }

        // Draw players
        if (p1Alive) canvas.drawCircle(p1x, p1y, 6f, p1Paint)
        if (p2Alive) canvas.drawCircle(p2x, p2y, 6f, p2Paint)

        // Draw scores
        canvas.drawText("P1: $score1", 20f, 60f, textPaint)
        canvas.drawText("P2: $score2", 20f, screenHeight - 20f, textPaint)

        if (gameState == STATE_GAME_OVER) {
            val gameOverPaint = Paint().apply { color = Color.WHITE; textSize = 64f; isAntiAlias = true; textAlign = Paint.Align.CENTER }
            canvas.drawText("GAME OVER", screenWidth / 2f, screenHeight / 2f, gameOverPaint)
            val restartPaint = Paint().apply { color = Color.GRAY; textSize = 32f; isAntiAlias = true; textAlign = Paint.Align.CENTER }
            canvas.drawText("Tap to restart", screenWidth / 2f, screenHeight / 2f + 60f, restartPaint)
        }

        holder.unlockCanvasAndPost(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                if (gameState == STATE_GAME_OVER) {
                    resetGame()
                    return true
                }
                val x = event.x
                val y = event.y
                if (y < screenHeight / 2) {
                    // Player 1 controls (top half) - steer based on touch position
                    val centerX = screenWidth / 2f
                    if (x < centerX - 50) {
                        // Turn left
                        if (p1dy != 0f) {
                            p1dx = -1f
                            p1dy = 0f
                        }
                    } else if (x > centerX + 50) {
                        // Turn right
                        if (p1dy != 0f) {
                            p1dx = 1f
                            p1dy = 0f
                        }
                    } else {
                        // Go straight (reset to vertical)
                        if (p1dx != 0f) {
                            p1dx = 0f
                            p1dy = if (p1y < screenHeight / 2) 1f else -1f
                        }
                    }
                } else {
                    // Player 2 controls (bottom half)
                    val centerX = screenWidth / 2f
                    if (x < centerX - 50) {
                        if (p2dy != 0f) {
                            p2dx = -1f
                            p2dy = 0f
                        }
                    } else if (x > centerX + 50) {
                        if (p2dy != 0f) {
                            p2dx = 1f
                            p2dy = 0f
                        }
                    } else {
                        if (p2dx != 0f) {
                            p2dx = 0f
                            p2dy = if (p2y < screenHeight / 2) 1f else -1f
                        }
                    }
                }
            }
        }
        return true
    }

    fun pause() {
        running = false
        try {
            thread?.join()
        } catch (e: InterruptedException) {
            // ignore
        }
    }

    fun resume() {
        if (!running) {
            running = true
            thread = Thread(this)
            thread?.start()
        }
    }
}