package com.klannato.bmtron

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Bundle
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
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
    @Volatile private var running = false

    private var screenWidth = 0
    private var screenHeight = 0

    private var controlZoneHeight = 0
    private var playFieldTop = 0f
    private var playFieldBottom = 0f

    private var gameState = STATE_PLAYING
    private var score1 = 0
    private var score2 = 0

    private var p1x = 0f
    private var p1y = 0f
    private var p1dx = 0f
    private var p1dy = 1f
    private val p1Trail = mutableListOf<Pair<Float, Float>>()
    private var p1Alive = true

    private var p2x = 0f
    private var p2y = 0f
    private var p2dx = 0f
    private var p2dy = -1f
    private val p2Trail = mutableListOf<Pair<Float, Float>>()
    private var p2Alive = true

    private val trailWidth = 4f
    private val playerRadius = 6f
    private val maxTrailSize = 2000

    private val p1Paint = Paint().apply { color = Color.CYAN; style = Paint.Style.FILL; isAntiAlias = true }
    private val p2Paint = Paint().apply { color = Color.MAGENTA; style = Paint.Style.FILL; isAntiAlias = true }
    private val bgPaint = Paint().apply { color = Color.BLACK }
    private val textPaint = Paint().apply { color = Color.WHITE; textSize = 40f; isAntiAlias = true }
    private val dividerPaint = Paint().apply { color = Color.DKGRAY; strokeWidth = 2f }
    private val controlBgPaint = Paint().apply { color = Color.rgb(30, 30, 30) }
    private val controlBorderPaint = Paint().apply { color = Color.rgb(80, 80, 80); style = Paint.Style.STROKE; strokeWidth = 2f }
    private val controlTextPaint = Paint().apply { color = Color.WHITE; textSize = 32f; isAntiAlias = true; textAlign = Paint.Align.CENTER }
    private val activeControlPaint = Paint().apply { color = Color.rgb(60, 60, 60) }
    private val labelPaint = Paint().apply { color = Color.CYAN; textSize = 24f; isAntiAlias = true; textAlign = Paint.Align.CENTER }
    private val label2Paint = Paint().apply { color = Color.MAGENTA; textSize = 24f; isAntiAlias = true; textAlign = Paint.Align.CENTER }
    private val scorePaint = Paint().apply { color = Color.WHITE; textSize = 48f; isAntiAlias = true; textAlign = Paint.Align.CENTER; isFakeBoldText = true }

    private var p1UpPressed = false
    private var p1DownPressed = false
    private var p1LeftPressed = false
    private var p1RightPressed = false
    private var p2UpPressed = false
    private var p2DownPressed = false
    private var p2LeftPressed = false
    private var p2RightPressed = false

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
        controlZoneHeight = (screenHeight * 0.15f).toInt()
        playFieldTop = controlZoneHeight.toFloat()
        playFieldBottom = screenHeight - controlZoneHeight.toFloat()

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
        try {
            thread?.join(1000)
        } catch (e: InterruptedException) {
            // ignore
        }
    }

    private fun resetGame() {
        p1x = screenWidth / 2f
        p1y = playFieldTop + (playFieldBottom - playFieldTop) * 0.15f
        p1dx = 0f
        p1dy = 1f
        p1Trail.clear()
        p1Alive = true

        p2x = screenWidth / 2f
        p2y = playFieldBottom - (playFieldBottom - playFieldTop) * 0.15f
        p2dx = 0f
        p2dy = -1f
        p2Trail.clear()
        p2Alive = true

        p1UpPressed = false
        p1DownPressed = false
        p1LeftPressed = false
        p1RightPressed = false
        p2UpPressed = false
        p2DownPressed = false
        p2LeftPressed = false
        p2RightPressed = false

        gameState = STATE_PLAYING
    }

    override fun run() {
        while (running) {
            try {
                if (gameState == STATE_PLAYING) {
                    update()
                }
                draw()
                Thread.sleep(16)
            } catch (e: InterruptedException) {
                // ignore
            } catch (e: Exception) {
                // prevent crash from killing thread
            }
        }
    }

    private fun update() {
        try {
            if (p1Alive) {
                var desiredDx = p1dx
                var desiredDy = p1dy
                if (p1UpPressed) { desiredDx = 0f; desiredDy = -1f }
                if (p1DownPressed) { desiredDx = 0f; desiredDy = 1f }
                if (p1LeftPressed) { desiredDx = -1f; desiredDy = 0f }
                if (p1RightPressed) { desiredDx = 1f; desiredDy = 0f }

                if (desiredDx != p1dx || desiredDy != p1dy) {
                    if (desiredDx == -p1dx && desiredDy == -p1dy) {
                        // block reversal
                    } else {
                        p1dx = desiredDx
                        p1dy = desiredDy
                    }
                }

                p1Trail.add(Pair(p1x, p1y))
                if (p1Trail.size > maxTrailSize) p1Trail.removeAt(0)
                p1x += p1dx * SPEED
                p1y += p1dy * SPEED

                if (p1x < 0 || p1x > screenWidth || p1y < playFieldTop || p1y > playFieldBottom) {
                    p1Alive = false
                }

                if (p1Alive) {
                    val sz = p1Trail.size
                    for (i in 0 until sz - 5) {
                        val (tx, ty) = p1Trail[i]
                        if (abs(p1x - tx) < trailWidth && abs(p1y - ty) < trailWidth) {
                            p1Alive = false
                            break
                        }
                    }
                }

                if (p1Alive) {
                    for ((tx, ty) in p2Trail) {
                        if (abs(p1x - tx) < trailWidth && abs(p1y - ty) < trailWidth) {
                            p1Alive = false
                            break
                        }
                    }
                }
            }

            if (p2Alive) {
                var desiredDx = p2dx
                var desiredDy = p2dy
                if (p2UpPressed) { desiredDx = 0f; desiredDy = -1f }
                if (p2DownPressed) { desiredDx = 0f; desiredDy = 1f }
                if (p2LeftPressed) { desiredDx = -1f; desiredDy = 0f }
                if (p2RightPressed) { desiredDx = 1f; desiredDy = 0f }

                if (desiredDx != p2dx || desiredDy != p2dy) {
                    if (desiredDx == -p2dx && desiredDy == -p2dy) {
                        // block reversal
                    } else {
                        p2dx = desiredDx
                        p2dy = desiredDy
                    }
                }

                p2Trail.add(Pair(p2x, p2y))
                if (p2Trail.size > maxTrailSize) p2Trail.removeAt(0)
                p2x += p2dx * SPEED
                p2y += p2dy * SPEED

                if (p2x < 0 || p2x > screenWidth || p2y < playFieldTop || p2y > playFieldBottom) {
                    p2Alive = false
                }

                if (p2Alive) {
                    val sz = p2Trail.size
                    for (i in 0 until sz - 5) {
                        val (tx, ty) = p2Trail[i]
                        if (abs(p2x - tx) < trailWidth && abs(p2y - ty) < trailWidth) {
                            p2Alive = false
                            break
                        }
                    }
                }

                if (p2Alive) {
                    for ((tx, ty) in p1Trail) {
                        if (abs(p2x - tx) < trailWidth && abs(p2y - ty) < trailWidth) {
                            p2Alive = false
                            break
                        }
                    }
                }
            }

            if (!p1Alive || !p2Alive) {
                if (!p1Alive && p2Alive) score2++
                else if (p1Alive && !p2Alive) score1++
                gameState = STATE_GAME_OVER
            }
        } catch (e: Exception) {
            // Prevent crash
        }
    }

    private fun draw() {
        try {
            val canvas = holder.lockCanvas() ?: return
            canvas.drawPaint(bgPaint)

            // Draw triangular buttons filling each control zone
            val cx = screenWidth / 2f
            val cyTop = controlZoneHeight / 2f
            val cyBot = screenHeight - controlZoneHeight / 2f

            // Top zone: 4 triangles meeting at center
            drawTriangle(canvas, 0f, 0f, cx, cyTop, screenWidth.toFloat(), 0f, p1UpPressed, "▲", cx, controlZoneHeight * 0.25f)
            drawTriangle(canvas, 0f, 0f, 0f, controlZoneHeight.toFloat(), cx, cyTop, p1LeftPressed, "◄", screenWidth * 0.12f, cyTop)
            drawTriangle(canvas, screenWidth.toFloat(), 0f, cx, cyTop, screenWidth.toFloat(), controlZoneHeight.toFloat(), p1RightPressed, "►", screenWidth * 0.88f, cyTop)
            drawTriangle(canvas, 0f, controlZoneHeight.toFloat(), cx, cyTop, screenWidth.toFloat(), controlZoneHeight.toFloat(), p1DownPressed, "▼", cx, controlZoneHeight * 0.75f)

            // Bottom zone
            drawTriangle(canvas, 0f, playFieldBottom, cx, cyBot, screenWidth.toFloat(), playFieldBottom, p2UpPressed, "▲", cx, playFieldBottom + controlZoneHeight * 0.25f)
            drawTriangle(canvas, 0f, playFieldBottom, 0f, screenHeight.toFloat(), cx, cyBot, p2LeftPressed, "◄", screenWidth * 0.12f, cyBot)
            drawTriangle(canvas, screenWidth.toFloat(), playFieldBottom, cx, cyBot, screenWidth.toFloat(), screenHeight.toFloat(), p2RightPressed, "►", screenWidth * 0.88f, cyBot)
            drawTriangle(canvas, 0f, screenHeight.toFloat(), cx, cyBot, screenWidth.toFloat(), screenHeight.toFloat(), p2DownPressed, "▼", cx, playFieldBottom + controlZoneHeight * 0.75f)

            // Borders between triangles
            canvas.drawLine(0f, 0f, cx, cyTop, controlBorderPaint)
            canvas.drawLine(screenWidth.toFloat(), 0f, cx, cyTop, controlBorderPaint)
            canvas.drawLine(0f, controlZoneHeight.toFloat(), cx, cyTop, controlBorderPaint)
            canvas.drawLine(screenWidth.toFloat(), controlZoneHeight.toFloat(), cx, cyTop, controlBorderPaint)

            canvas.drawLine(0f, playFieldBottom, cx, cyBot, controlBorderPaint)
            canvas.drawLine(screenWidth.toFloat(), playFieldBottom, cx, cyBot, controlBorderPaint)
            canvas.drawLine(0f, screenHeight.toFloat(), cx, cyBot, controlBorderPaint)
            canvas.drawLine(screenWidth.toFloat(), screenHeight.toFloat(), cx, cyBot, controlBorderPaint)

            // Score boards - top facing P1 (normal), bottom facing P2 (rotated 180)
            canvas.drawText("P1: $score1", screenWidth / 2f, controlZoneHeight * 0.55f, scorePaint)

            canvas.save()
            canvas.rotate(180f, screenWidth / 2f, screenHeight - controlZoneHeight * 0.55f)
            canvas.drawText("P2: $score2", screenWidth / 2f, screenHeight - controlZoneHeight * 0.55f, scorePaint)
            canvas.restore()

            canvas.drawText("P1", screenWidth / 2f, controlZoneHeight * 0.2f, labelPaint)
            canvas.drawText("P2", screenWidth / 2f, screenHeight - controlZoneHeight * 0.2f, label2Paint)

            // Play field
            canvas.drawRect(0f, playFieldTop, screenWidth.toFloat(), playFieldBottom, bgPaint)

            // Draw trails
            val p1TrailSize = p1Trail.size
            for (i in 1 until p1TrailSize) {
                val (x1, y1) = p1Trail[i - 1]
                val (x2, y2) = p1Trail[i]
                canvas.drawLine(x1, y1, x2, y2, p1Paint.apply { strokeWidth = trailWidth })
            }

            val p2TrailSize = p2Trail.size
            for (i in 1 until p2TrailSize) {
                val (x1, y1) = p2Trail[i - 1]
                val (x2, y2) = p2Trail[i]
                canvas.drawLine(x1, y1, x2, y2, p2Paint.apply { strokeWidth = trailWidth })
            }

            if (p1Alive) canvas.drawCircle(p1x, p1y, playerRadius, p1Paint)
            if (p2Alive) canvas.drawCircle(p2x, p2y, playerRadius, p2Paint)

            if (gameState == STATE_GAME_OVER) {
                val gameOverPaint = Paint().apply { color = Color.WHITE; textSize = 64f; isAntiAlias = true; textAlign = Paint.Align.CENTER }
                canvas.drawText("GAME OVER", screenWidth / 2f, (playFieldTop + playFieldBottom) / 2f, gameOverPaint)
                val restartPaint = Paint().apply { color = Color.GRAY; textSize = 32f; isAntiAlias = true; textAlign = Paint.Align.CENTER }
                canvas.drawText("Tap anywhere to restart", screenWidth / 2f, (playFieldTop + playFieldBottom) / 2f + 60f, restartPaint)
            }

            holder.unlockCanvasAndPost(canvas)
        } catch (e: Exception) {
            // Prevent crash
        }
    }

    private fun drawTriangle(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, pressed: Boolean, label: String, lx: Float, ly: Float) {
        val path = Path()
        path.moveTo(x1, y1)
        path.lineTo(x2, y2)
        path.lineTo(x3, y3)
        path.close()
        val paint = if (pressed) activeControlPaint else controlBgPaint
        canvas.drawPath(path, paint)
        canvas.drawText(label, lx, ly + 10f, controlTextPaint)
    }

    private fun pointInTriangle(px: Float, py: Float, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float): Boolean {
        val d1 = (px - x2) * (y1 - y2) + (py - y2) * (x2 - x1)
        val d2 = (px - x3) * (y2 - y3) + (py - y3) * (x3 - x2)
        val d3 = (px - x1) * (y3 - y1) + (py - y1) * (x1 - x3)
        val hasNeg = (d1 < 0) || (d2 < 0) || (d3 < 0)
        val hasPos = (d1 > 0) || (d2 > 0) || (d3 > 0)
        return !(hasNeg && hasPos)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        try {
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    if (gameState == STATE_GAME_OVER) {
                        resetGame()
                        return true
                    }
                    val x = event.x
                    val y = event.y

                    if (y <= controlZoneHeight) {
                        val cx = screenWidth / 2f
                        val cy = controlZoneHeight / 2f
                        p1UpPressed = pointInTriangle(x, y, 0f, 0f, cx, cy, screenWidth.toFloat(), 0f)
                        p1LeftPressed = pointInTriangle(x, y, 0f, 0f, 0f, controlZoneHeight.toFloat(), cx, cy)
                        p1RightPressed = pointInTriangle(x, y, screenWidth.toFloat(), 0f, cx, cy, screenWidth.toFloat(), controlZoneHeight.toFloat())
                        p1DownPressed = pointInTriangle(x, y, 0f, controlZoneHeight.toFloat(), cx, cy, screenWidth.toFloat(), controlZoneHeight.toFloat())
                    } else {
                        p1UpPressed = false
                        p1DownPressed = false
                        p1LeftPressed = false
                        p1RightPressed = false
                    }

                    if (y >= playFieldBottom) {
                        val cx = screenWidth / 2f
                        val cy = screenHeight - controlZoneHeight / 2f
                        p2UpPressed = pointInTriangle(x, y, 0f, playFieldBottom, cx, cy, screenWidth.toFloat(), playFieldBottom)
                        p2LeftPressed = pointInTriangle(x, y, 0f, playFieldBottom, 0f, screenHeight.toFloat(), cx, cy)
                        p2RightPressed = pointInTriangle(x, y, screenWidth.toFloat(), playFieldBottom, cx, cy, screenWidth.toFloat(), screenHeight.toFloat())
                        p2DownPressed = pointInTriangle(x, y, 0f, screenHeight.toFloat(), cx, cy, screenWidth.toFloat(), screenHeight.toFloat())
                    } else {
                        p2UpPressed = false
                        p2DownPressed = false
                        p2LeftPressed = false
                        p2RightPressed = false
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    p1UpPressed = false
                    p1DownPressed = false
                    p1LeftPressed = false
                    p1RightPressed = false
                    p2UpPressed = false
                    p2DownPressed = false
                    p2LeftPressed = false
                    p2RightPressed = false
                }
            }
        } catch (e: Exception) {
            // Prevent crash
        }
        return true
    }

    fun pause() {
        running = false
        try {
            thread?.join(1000)
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