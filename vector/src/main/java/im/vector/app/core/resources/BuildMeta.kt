/*
 * Copyright (c) 2022 New Vector Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package im.vector.app.core.resources

import im.vector.app.BuildConfig

data class BuildMeta(
        val isDebug: Boolean,
        val applicationId: String,
        val applicationName: String,
        val lowPrivacyLoggingEnabled: Boolean,
        val versionName: String,
        val gitRevision: String,
        val gitRevisionDate: String,
        val gitBranchName: String,
        val flavorDescription: String,
        val flavorShortDescription: String,
) {
    val isInternalBuild: Boolean = BuildConfig.DEBUG || gitBranchName == "sm_fdroid"
    // Play Store has some annoying forms to fill out if we have all features, like easy-access to registering an account at matrix.org.
    // Accordingly, we want to disable some features for releases that go to the Play Store, while keeping them in all fdroid-based releases.
    val isPlayStoreBuild: Boolean =  "gplay" in gitBranchName
}
