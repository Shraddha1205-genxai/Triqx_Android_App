package com.example.triqx.ui.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.triqx.data.local.AppDao
import com.example.triqx.data.local.AppEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppInfo(
    val packageName: String,
    val appName: String,
    val isImportant: Boolean = false
)

@HiltViewModel
class AppViewModel @Inject constructor(
    private val appDao: AppDao,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val packageManager: PackageManager = context.packageManager

    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    private val _isLoadingApps = MutableStateFlow(false)
    val isLoadingApps: StateFlow<Boolean> = _isLoadingApps.asStateFlow()

    // 1. Saved important apps from Room DB (guaranteed unique by packageName)
    val savedImportantApps: StateFlow<List<AppInfo>> = appDao.getAllImportantApps()
        .map { entities ->
            entities.distinctBy { it.packageName }.map { entity ->
                AppInfo(
                    packageName = entity.packageName,
                    appName = entity.appName,
                    isImportant = true
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 2. Combined with all installed apps for the selection screen
    val allApps: StateFlow<List<AppInfo>> = combine(_installedApps, appDao.getAllImportantApps()) { installed, important ->
        val importantPackages = important.map { it.packageName }.toSet()
        installed
            .distinctBy { it.packageName }
            .map { it.copy(isImportant = importantPackages.contains(it.packageName)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 3. Load installed launchable apps - strictly deduplicated by packageName
    fun loadInstalledAppsIfNeeded() {
        if (_installedApps.value.isNotEmpty() || _isLoadingApps.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingApps.value = true
            try {
                val intent = Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                val resolveInfos = packageManager.queryIntentActivities(intent, 0)

                val apps = resolveInfos
                    .filter { it.activityInfo != null && !it.activityInfo.packageName.isNullOrBlank() }
                    .distinctBy { it.activityInfo.packageName }
                    .map { resolveInfo ->
                        val pkg = resolveInfo.activityInfo.packageName
                        val name = try {
                            resolveInfo.loadLabel(packageManager).toString()
                        } catch (e: Exception) {
                            pkg
                        }
                        AppInfo(
                            packageName = pkg,
                            appName = name.ifBlank { pkg }
                        )
                    }
                    .sortedBy { it.appName.lowercase() }

                _installedApps.value = apps
            } catch (e: Throwable) {
                e.printStackTrace()
            } finally {
                _isLoadingApps.value = false
            }
        }
    }

    fun toggleImportant(app: AppInfo) {
        viewModelScope.launch(Dispatchers.IO) {
            if (app.isImportant) {
                appDao.deleteApp(AppEntity(app.packageName, app.appName))
            } else {
                appDao.insertApp(AppEntity(app.packageName, app.appName))
            }
        }
    }

    fun saveSelectedApps(selectedPackages: Set<String>, allAppsList: List<AppInfo>) {
        viewModelScope.launch(Dispatchers.IO) {
            val appMap = allAppsList.associateBy { it.packageName }
            val entities = selectedPackages
                .filter { it.isNotBlank() }
                .distinct()
                .map { pkg ->
                    val name = appMap[pkg]?.appName ?: pkg
                    AppEntity(packageName = pkg, appName = name)
                }.sortedBy { it.appName.lowercase() }
            appDao.replaceAllImportantApps(entities)
        }
    }
}
