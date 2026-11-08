// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.base

import android.content.res.AssetManager
import android.os.Build
import android.os.Environment
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.util.FileUtils
import com.osfans.trime.util.ResourceUtils
import com.osfans.trime.util.appContext
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object DataManager {
    private const val DEFAULT_CUSTOM_FILE_NAME = "default.custom.yaml"

    private const val DATA_CHECKSUMS_NAME = "checksums.json"

    /**
     * 上游 Trime 首次同步时会生成的 default.custom.yaml 内容，把方案列表改成朙月拼音。
     * 本 fork 内置的是 MyRimeConfig，不带朙月拼音，方案列表应由它的 default.yaml 决定，
     * 所以不再生成这个文件；旧版本生成过的、内容未被改动的文件会在同步时删除。
     */
    private const val LEGACY_SCHEMA_LIST_CUSTOM_PATCH = """
      patch:
        schema_list:
          - schema: luna_pinyin
          - schema: luna_pinyin_simp
    """

    private val lock = ReentrantLock()

    private val json by lazy { Json }

    private fun deserializeDataChecksums(raw: String): DataChecksums = json.decodeFromString<DataChecksums>(raw)

    // If Android version supports direct boot, we put the hierarchy in device encrypted storage
    // instead of credential encrypted storage so that data can be accessed before user unlock
    private val dataDir: File =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Timber.d("Using device protected storage")
            appContext.createDeviceProtectedStorageContext().dataDir
        } else {
            File(appContext.applicationInfo.dataDir)
        }

    private fun AssetManager.dataChecksums(): DataChecksums = open(DATA_CHECKSUMS_NAME)
        .bufferedReader()
        .use { it.readText() }
        .let { deserializeDataChecksums(it) }

    private val prefs by lazy { AppPrefs.defaultInstance() }

    val defaultDataDir = File(Environment.getExternalStorageDirectory(), "rime")

    val sharedDataDir = File(appContext.getExternalFilesDir(null), "shared").also { it.mkdirs() }

    val userDataDir
        get() = File(prefs.profile.userDataDir.getValue()).also { it.mkdirs() }

    val prebuiltDataDir = File(sharedDataDir, "build")
    val stagingDir get() = File(userDataDir, "build")

    /**
     * Return the absolute path of the compiled config file
     * based on given resource id.
     *
     * @param resourceId usually equals the config file name without the extension
     * @return the absolute path of the compiled config file
     */
    @JvmStatic
    fun resolveDeployedResourcePath(resourceId: String): String {
        val defaultPath = File(stagingDir, "$resourceId.yaml")
        if (!defaultPath.exists()) {
            val fallbackPath = File(prebuiltDataDir, "$resourceId.yaml")
            if (fallbackPath.exists()) return fallbackPath.absolutePath
        }
        return defaultPath.absolutePath
    }

    fun sync() = lock.withLock {
        val oldChecksumsFile = File(dataDir, DATA_CHECKSUMS_NAME)
        val oldChecksums =
            oldChecksumsFile
                .runCatching { deserializeDataChecksums(bufferedReader().use { it.readText() }) }
                .getOrElse { DataChecksums("", emptyMap()) }

        val newChecksums = appContext.assets.dataChecksums()

        DataDiff.diff(oldChecksums, newChecksums).sortedByDescending { it.ordinal }.forEach {
            Timber.d("Diff: $it")
            when (it) {
                is DataDiff.CreateFile,
                is DataDiff.UpdateFile,
                -> {
                    val destPath = sharedDataDir.resolveSibling(it.path).absolutePath
                    ResourceUtils.copyFile(it.path, destPath)
                }
                is DataDiff.DeleteDir,
                is DataDiff.DeleteFile,
                -> FileUtils.delete(sharedDataDir.resolve(it.path.substringAfterLast('/'))).getOrThrow()
            }
        }

        ResourceUtils.copyFile(DATA_CHECKSUMS_NAME, dataDir.resolve(DATA_CHECKSUMS_NAME).absolutePath)

        // 方案列表以 MyRimeConfig 的 default.yaml 为准。在 App 里勾选方案时，
        // librime 的 levers API 会自己创建 default.custom.yaml，这里不需要预先生成。
        val custom = userDataDir.resolve(DEFAULT_CUSTOM_FILE_NAME)
        val legacyPatch = LEGACY_SCHEMA_LIST_CUSTOM_PATCH.trimIndent()
        if (custom.isFile && custom.runCatching { readText().trim() }.getOrNull() == legacyPatch) {
            Timber.i("Removing legacy luna_pinyin schema_list patch: $custom")
            custom.delete()
        }

        Timber.d("Synced!")
    }
}
