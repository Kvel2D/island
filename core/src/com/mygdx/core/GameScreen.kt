package com.mygdx.core

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.maps.tiled.TiledMap
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer
import com.badlogic.gdx.maps.tiled.TmxMapLoader
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer

object Controls {
    const final val JUMP = Input.Keys.SPACE
    const final val LEFT = Input.Keys.LEFT
    const final val RIGHT = Input.Keys.RIGHT
    const final val UP = Input.Keys.UP
    const final val DOWN = Input.Keys.DOWN
    const final val RESTART = Input.Keys.ESCAPE
}

class Position(var x: Int, var y: Int)

class SinkQueue(var currentIndex: Int, var queue: MutableMap<Int, MutableList<Position>>)

fun MutableList<Position>.containsValue(pos: Position): Boolean {
    this.forEach {
        if (it.x == pos.x && it.y == pos.y) {
            return true
        }
    }
    return false
}

class GameScreen : ScreenAdapter() {
    val camera = OrthographicCamera(Constants.VIEWPORT_WIDTH.toFloat(), Constants.VIEWPORT_HEIGHT.toFloat())

    enum class GAME_STATE {
        NORMAL, JUMPING, FALLING, DROWN
    }

    var state = GAME_STATE.NORMAL

    val mapLoader = TmxMapLoader()
    var map = TiledMap()
    val mapRenderer = OrthogonalTiledMapRenderer(map)
    lateinit var ground: TiledMapTileLayer
    lateinit var boxes: TiledMapTileLayer
    lateinit var flowers: TiledMapTileLayer
    lateinit var steps: TiledMapTileLayer
    lateinit var stepUp: TiledMapTileLayer.Cell
    lateinit var stepDown: TiledMapTileLayer.Cell
    lateinit var stepLeft: TiledMapTileLayer.Cell
    lateinit var stepRight: TiledMapTileLayer.Cell

    val playerFrames: Array<Array<TextureRegion>>
    var currentFrame: TextureRegion
    var screenPosition = Position(0, 0) // position of player in the viewport
    var worldPosition = Position(0, 0) // position of the viewport in the world

    val jumpZoomMax = 13f
    val jumpTime = 1f
    val zoomSpeed = (jumpZoomMax - 1f) / (jumpTime * 60f) // this value is for 60fps, needs to be adjusted for deltatime

    val inputDelay = 0.1f
    var inputTimer = inputDelay
    val jumpDelay = 8f //8
    var jumpTimer = 0f
    var jumpFrame = 1

    var drownAlpha = 1f

    // Variables for sink calculations
    var islandCenter = Position(0, 0)
    var largestDistance = 0
    var scannedPositions = mutableListOf<Position>()
    var sinkQueues = mutableListOf<SinkQueue>()
    val sinkDelay = 1f
    var sinkTimer = sinkDelay

    init {
        // Create player frames
        val playerSheet: Texture = Main.assets.get(AssetPaths.PLAYER)
        val columns = 5
        val rows = 3
        val tmp = TextureRegion.split(playerSheet, playerSheet.width / columns, playerSheet.height / rows);
        playerFrames = Array(tmp.size, { i -> Array(columns, { j -> tmp[i][j] }) })
        currentFrame = playerFrames[0][0]

        initGame()
    }

    fun initGame() {
        // Reset player
        screenPosition.x = 10
        screenPosition.y = 9
        worldPosition.x = 200
        worldPosition.y = 200
        inputTimer = inputDelay
        jumpTimer = 0f
        currentFrame = playerFrames[0][0]

        // Reload map and stuff
        map.dispose()
        map = mapLoader.load(AssetPaths.MAP); // reload the FILE of the map
        mapRenderer.map = map
        ground = map.layers.get("ground") as TiledMapTileLayer
        boxes = map.layers.get("boxes") as TiledMapTileLayer
        flowers = map.layers.get("flowers") as TiledMapTileLayer
        steps = map.layers.get("steps") as TiledMapTileLayer

        // Get reference step cells and erase them from tilemap
        stepLeft = steps.getCell(0, 0)
        steps.setCell(0, 0, null)
        stepRight = steps.getCell(2, 0)
        steps.setCell(2, 0, null)
        stepDown = steps.getCell(1, 0)
        steps.setCell(1, 0, null)
        stepUp = steps.getCell(1, 1)
        steps.setCell(1, 1, null)

        // Clear any sinks from previous game
        sinkQueues.clear()
        createSink()
    }

    override fun render(deltaTime: Float) {
        // Sink islands
        if (!sinkQueues.isEmpty()) {
            if (sinkTimer > 0f) {
                sinkTimer -= deltaTime
            } else {
                // Reset timer
                sinkTimer = sinkDelay

                sinkQueues.forEach {
                    while (it.currentIndex >= 0) {

                        if (it.queue.containsKey(it.currentIndex)) {

                            it.queue[it.currentIndex]!!.forEach {
                                ground.setCell(it.x, it.y, null)
                                steps.setCell(it.x, it.y, null)
                            }
                            it.queue.remove(it.currentIndex)

                            break
                        }

                        it.currentIndex--
                    }
                }

                var i = sinkQueues.size - 1
                while (i >= 0) {
                    if (sinkQueues[i].currentIndex == 0) {
                        sinkQueues.remove(sinkQueues[i])
                    }
                    i--
                }
            }
        }

        when (state) {
            GAME_STATE.NORMAL -> updateNormal(deltaTime)
            GAME_STATE.JUMPING -> updateJumping(deltaTime)
            GAME_STATE.FALLING -> updateFalling(deltaTime)
            GAME_STATE.DROWN -> updateDrown(deltaTime)
        }

        camera.position.x = 320f - (screenPosition.x - 9.5f) * 32f * (camera.zoom - 1f) + worldPosition.x * 32f
        camera.position.y = 320f - (screenPosition.y - 9.5f) * 32f * (camera.zoom - 1f) + worldPosition.y * 32f
        camera.update()
        mapRenderer.setView(camera)

        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        mapRenderer.render()
        Main.batch.begin()
        Main.batch.draw(currentFrame, screenPosition.x * 32f, screenPosition.y * 32f)
        Main.batch.end()
    }


    fun updateNormal(deltaTime: Float) {
        val position = Position(screenPosition.x + worldPosition.x, screenPosition.y + worldPosition.y)

        if (Gdx.input.isKeyJustPressed(Controls.RESTART)) {
            initGame()
            return
        }

        if (ground.getCell(position.x, position.y) == null) {
            state = GAME_STATE.DROWN
            return
        }

        // Animate jump recharge
        if (jumpTimer > 0f) {
            jumpTimer -= deltaTime
            if (jumpTimer / jumpDelay > 0.9f) {
                currentFrame = playerFrames[1][4]
            } else if (jumpTimer / jumpDelay > 0.8f) {
                currentFrame = playerFrames[1][3]
            } else if (jumpTimer / jumpDelay > 0.7f) {
                currentFrame = playerFrames[1][2]
            } else if (jumpTimer / jumpDelay > 0.6f) {
                currentFrame = playerFrames[1][1]
            } else if (jumpTimer / jumpDelay > 0.5f) {
                currentFrame = playerFrames[1][0]
            } else if (jumpTimer / jumpDelay > 0.4f) {
                currentFrame = playerFrames[0][4]
            } else if (jumpTimer / jumpDelay > 0.3f) {
                currentFrame = playerFrames[0][3]
            } else if (jumpTimer / jumpDelay > 0.15f) {
                currentFrame = playerFrames[0][2]
            } else if (jumpTimer / jumpDelay > 0.05f) {
                currentFrame = playerFrames[0][1]
            } else {
                currentFrame = playerFrames[0][0]
            }
        } else if (Gdx.input.isKeyJustPressed(Controls.JUMP)) {
            // Jump
            jumpTimer = jumpDelay
            jumpFrame = 1
            state = GAME_STATE.JUMPING
            return
        }

        // Movement input
        if (inputTimer > 0f) {
            inputTimer -= deltaTime
        } else {
            var dx = 0
            var dy = 0
            val left = Gdx.input.isKeyPressed(Controls.LEFT)
            val right = Gdx.input.isKeyPressed(Controls.RIGHT)
            val up = Gdx.input.isKeyPressed(Controls.UP)
            val down = Gdx.input.isKeyPressed(Controls.DOWN)
            // Move only in one direction at a time
            if (left && !right && !up && !down) {
                dx = -1
            } else if (right && !left && !up && !down) {
                dx = 1
            } else if (down && !up && !left && !right) {
                dy = -1
            } else if (up && !down && !left && !right) {
                dy = 1
            }
            // Move if displacement isn't zero
            if (dx != 0 || dy != 0) {
                var moved = false

                // Regular movement
                // Move if there's ground ahead and no box
                if (ground.getCell(position.x + dx, position.y + dy) != null
                        && boxes.getCell(position.x + dx, position.y + dy) == null) {
                    moved = true
                }
                // There's a box ahead
                // Move player and the box if
                // 1.there is ground in front of the box
                // 2.there isn't another box blocking the way
                if (boxes.getCell(position.x + dx, position.y + dy) != null
                        && ground.getCell(position.x + dx * 2, position.y + dy * 2) != null
                        && boxes.getCell(position.x + dx * 2, position.y + dy * 2) == null) {

                    val boxCell = boxes.getCell(position.x + dx, position.y + dy)
                    boxes.setCell(position.x + dx * 2, position.y + dy * 2, boxCell)
                    boxes.setCell(position.x + dx, position.y + dy, null)
                    moved = true
                }

                if (moved) {
                    inputTimer = inputDelay

                    // Add steps to current position
                    if (worldPosition.x > 80 && worldPosition.y > 80) {
                        if (dx == -1) {
                            steps.setCell(position.x, position.y, stepLeft)
                        } else if (dx == 1) {
                            steps.setCell(position.x, position.y, stepRight)
                        } else if (dy == -1) {
                            steps.setCell(position.x, position.y, stepDown)
                        } else if (dy == 1) {
                            steps.setCell(position.x, position.y, stepUp)
                        }
                    }

                    // If moving on to a flower
                    // remove it and recharge jump
                    if (flowers.getCell(position.x + dx, position.y + dy) != null) {
                        flowers.setCell(position.x + dx, position.y + dy, null)
                        jumpTimer = 0.01f
                    }

                    // Move player
                    screenPosition.x += dx
                    screenPosition.y += dy

                    // Move camera if 3 tiles away from an edge of the screen
                    if (dx == 1 && screenPosition.x == 17) {
                        camera.position.x += 32f
                        worldPosition.x++
                        screenPosition.x--
                    } else if (dx == -1 && screenPosition.x == 2) {
                        camera.position.x -= 32f
                        worldPosition.x--
                        screenPosition.x++
                    } else if (dy == 1 && screenPosition.y == 17) {
                        camera.position.y += 32f
                        worldPosition.y++
                        screenPosition.y--
                    } else if (dy == -1 && screenPosition.y == 2) {
                        camera.position.y -= 32f
                        worldPosition.y--
                        screenPosition.y++
                    }
                }
            }
        }
    }

    fun updateJumping(deltaTime: Float) {
        handleInputAir()

        when (jumpFrame) {
            1 -> currentFrame = playerFrames[0][1]
            2 -> currentFrame = playerFrames[0][3]
            3 -> currentFrame = playerFrames[1][0]
            4 -> currentFrame = playerFrames[1][2]
            else -> currentFrame = playerFrames[2][0]
        }
        jumpFrame++

        if (camera.zoom < jumpZoomMax) {
            // Zoom out
            camera.zoom += zoomSpeed * deltaTime / Constants.FRAMETIME  // adjust zoom speed for deltaTime
        } else {
            state = GAME_STATE.FALLING
        }
    }

    fun updateFalling(deltaTime: Float) {
        handleInputAir()

        if (camera.zoom > 1f) {
            // Zoom in
            camera.zoom -= Math.min(
                    zoomSpeed * deltaTime / Constants.FRAMETIME, // adjust zoom speed for deltaTime
                    camera.zoom - 1f)
        } else {
            camera.zoom = 1f

            val position = Position(screenPosition.x + worldPosition.x, screenPosition.y + worldPosition.y)
            if (ground.getCell(position.x, position.y) != null) {
                currentFrame = playerFrames[1][4]
                state = GAME_STATE.NORMAL
                if (worldPosition.x > 80 && worldPosition.y > 80 && worldPosition.y < 530) {
                    createSink()
                }
                // If fell on a flower
                // remove it and recharge jump
                if (flowers.getCell(position.x, position.y) != null) {
                    flowers.setCell(position.x, position.y, null)
                    jumpTimer = 0.01f
                }
            } else {
                state = GAME_STATE.DROWN
            }
            return
        }
    }

    fun handleInputAir() {
        var dx = 0
        var dy = 0
        val left = Gdx.input.isKeyPressed(Controls.LEFT)
        val right = Gdx.input.isKeyPressed(Controls.RIGHT)
        val up = Gdx.input.isKeyPressed(Controls.UP)
        val down = Gdx.input.isKeyPressed(Controls.DOWN)
        if (left && !right) {
            dx = -1
        }
        if (right && !left) {
            dx = 1
        }
        if (down && !up) {
            dy = -1
        }
        if (up && !down) {
            dy = 1
        }
        worldPosition.x += dx;
        worldPosition.y += dy
    }

    fun updateDrown(deltaTime: Float) {
        if (drownAlpha > 0.02f) {
            drownAlpha -= 0.02f * deltaTime / Constants.FRAMETIME
            Main.batch.setColor(1f, 1f, 1f, drownAlpha)
        } else {
            drownAlpha = 1f
            Main.batch.setColor(1f, 1f, 1f, 1f)
            initGame()
            state = GAME_STATE.NORMAL
            return
        }
    }

    fun createSink() {
        // Position player landed at(used as a starting point for recursions)
        val startX = screenPosition.x + worldPosition.x
        val startY = screenPosition.y + worldPosition.y
        val start = Position(startX, startY)

        var newQueue = mutableMapOf<Int, MutableList<Position>>()

        // Find island center
        largestDistance = 0
        scannedPositions.clear()
        findCenter(start)

        // Create a map of ground tiles sorted by distances to center of the island
        scannedPositions.clear()
        addToQueue(start, newQueue)
        // Save largest sinkDistance
        var largestDistance = 0
        newQueue.forEach {
            if (it.key > largestDistance) {
                largestDistance = it.key
            }
        }
        sinkQueues.add(SinkQueue(largestDistance, newQueue))
    }

    fun findCenter(origin: Position) {
        scannedPositions.add(origin)
        for (dx in -1..1) {
            for (dy in -1..1) {
                val pos = Position(origin.x + dx, origin.y + dy)
                // Check the cell if it wasn't already scanned and is a ground tile
                if (!scannedPositions.containsValue(pos) && ground.getCell(pos.x, pos.y) != null) {
                    // Water distance = sum of 4 distances(left, right, up, down) to water from this position
                    var waterDistance = 0
                    var x1 = 0
                    while (ground.getCell(pos.x + x1, pos.y) != null) {
                        x1++
                    }
                    var x2 = 0
                    while (ground.getCell(pos.x + x2, pos.y) != null) {
                        x2--
                    }
                    waterDistance += Math.min(x1, -x2)
                    var y1 = 0
                    while (ground.getCell(pos.x, pos.y + y1) != null) {
                        y1++
                    }
                    var y2 = 0
                    while (ground.getCell(pos.x, pos.y + y2) != null) {
                        y2--
                    }
                    waterDistance += Math.min(y1, -y2)

                    if (waterDistance > largestDistance) {
                        largestDistance = waterDistance
                        islandCenter = pos
                    }

                    findCenter(pos)
                }
            }
        }
    }

    fun addToQueue(origin: Position, queue: MutableMap<Int, MutableList<Position>>) {
        for (dx in -1..1) {
            for (dy in -1..1) {
                val pos = Position(origin.x + dx, origin.y + dy)
                if (!scannedPositions.containsValue(pos) && ground.getCell(pos.x, pos.y) != null) {
                    scannedPositions.add(pos)
                    val distance = Math.sqrt(((islandCenter.x - pos.x) * (islandCenter.x - pos.x) + (islandCenter.y - pos.y) * (islandCenter.y - pos.y)).toDouble()).toInt()
                    if (queue.containsKey(distance)) {
                        queue[distance]!!.add(pos)
                    } else {
                        queue.put(distance, mutableListOf(pos))
                    }
                    addToQueue(pos, queue)
                }
            }
        }
    }
}
