package com.mygdx.core

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.maps.tiled.TiledMap
import com.badlogic.gdx.maps.tiled.TmxMapLoader

class Main : ApplicationAdapter() {
    companion object {
        val game: Game = object : Game() {
            override fun create() {
            }
        }
        val assets = AssetManager()
        lateinit var batch: SpriteBatch
        lateinit var gameScreen: GameScreen
    }

    override fun create() {
        batch = SpriteBatch()
        Gdx.gl.glClearColor(Constants.WATER_COLOR.r, Constants.WATER_COLOR.g, Constants.WATER_COLOR.b, Constants.WATER_COLOR.a)
        Texture.setAssetManager(assets)

        // Load assets
        assets.load(AssetPaths.PLAYER, Texture::class.java)
        assets.setLoader(TiledMap::class.java, TmxMapLoader(InternalFileHandleResolver()))
        assets.finishLoading()

        gameScreen = GameScreen()
        game.screen = gameScreen;
    }

    override fun render() {
        game.render()
    }

    override fun dispose() {
        batch.dispose()
        assets.dispose()
    }
}