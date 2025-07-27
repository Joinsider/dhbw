/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.fampopprol.dhbwhorb.R
import de.fampopprol.dhbwhorb.data.cache.GradesCacheManager
import de.fampopprol.dhbwhorb.data.dualis.network.DualisService
import de.fampopprol.dhbwhorb.data.security.CredentialManager
import de.fampopprol.dhbwhorb.ui.screen.gradesScreen.GradesAuthManager
import de.fampopprol.dhbwhorb.ui.screen.gradesScreen.GradesContent
import de.fampopprol.dhbwhorb.ui.screen.gradesScreen.GradesDataManager
import de.fampopprol.dhbwhorb.ui.screen.gradesScreen.GradesStateManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradesScreen(
    dualisService: DualisService,
    modifier: Modifier = Modifier,
    credentialManager: CredentialManager? = null,
    gradesCacheManager: GradesCacheManager? = null
) {
    val scope = rememberCoroutineScope()
    val pullRefreshState = rememberPullToRefreshState()

    // Create managers
    val dataManager = remember {
        GradesDataManager(dualisService, gradesCacheManager, scope)
    }

    val authManager = remember {
        GradesAuthManager(dualisService, credentialManager, scope)
    }

    val stateManager = remember {
        GradesStateManager(dataManager, authManager, scope)
    }

    // Error messages from string resources
    val authenticationFailedMessage = stringResource(R.string.authentication_failed)
    val noCredentialsFoundMessage = stringResource(R.string.no_credentials_found)
    val pleaseLoginMessage = stringResource(R.string.please_login)
    val failedToLoadGradesMessage = stringResource(R.string.failed_to_load_grades)

    // Initialize on first composition
    LaunchedEffect(Unit) {
        stateManager.initialize(
            failedToLoadGradesMessage = failedToLoadGradesMessage,
            authenticationFailedMessage = authenticationFailedMessage,
            noCredentialsFoundMessage = noCredentialsFoundMessage,
            pleaseLoginMessage = pleaseLoginMessage
        ) { authResult ->
            // Error handling is now done in the initialize method
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        PullToRefreshBox(
            state = pullRefreshState,
            onRefresh = {
                stateManager.onRefresh(
                    failedToLoadGradesMessage = failedToLoadGradesMessage,
                    authenticationFailedMessage = authenticationFailedMessage,
                    noCredentialsFoundMessage = noCredentialsFoundMessage,
                    pleaseLoginMessage = pleaseLoginMessage
                )
            },
            isRefreshing = stateManager.isRefreshing,
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                when {
                    stateManager.isLoading && !stateManager.isRefreshing -> {
                        LoadingContent(
                            isLoadingSemesters = stateManager.isLoadingSemesters
                        )
                    }

                    stateManager.errorMessage != null && stateManager.studyGrades == null -> {
                        ErrorContent(
                            errorMessage = stateManager.errorMessage!!,
                            onRetry = {
                                stateManager.onRetry(
                                    failedToLoadGradesMessage = failedToLoadGradesMessage,
                                    authenticationFailedMessage = authenticationFailedMessage,
                                    noCredentialsFoundMessage = noCredentialsFoundMessage,
                                    pleaseLoginMessage = pleaseLoginMessage
                                )
                            }
                        )
                    }

                    else -> {
                        GradesContent(
                            studyGrades = stateManager.studyGrades,
                            isRefreshing = stateManager.isRefreshing,
                            availableSemesters = stateManager.availableSemesters,
                            selectedSemester = stateManager.selectedSemester,
                            onSemesterSelected = { semester ->
                                stateManager.onSemesterSelected(
                                    semester = semester,
                                    failedToLoadGradesMessage = failedToLoadGradesMessage
                                )
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingContent(
    isLoadingSemesters: Boolean
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                modifier = Modifier.padding(16.dp)
            )
            Text(
                text = if (isLoadingSemesters) {
                    stringResource(R.string.loading_semesters)
                } else {
                    stringResource(R.string.loading_grades)
                },
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun ErrorContent(
    errorMessage: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = errorMessage,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = stringResource(R.string.retry)
            )
            Spacer(modifier = Modifier.padding(horizontal = 4.dp))
            Text(stringResource(R.string.retry))
        }
    }
}
