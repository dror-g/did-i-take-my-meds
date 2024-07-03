/*
 * Did I Take My Meds? is a FOSS app to keep track of medications
 * Did I Take My Meds? is designed to help prevent a user from skipping doses and/or overdosing
 *     Copyright (C) 2021  Noah Stanford <noahstandingford@gmail.com>
 *
 *     Did I Take My Meds? is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Did I Take My Meds? is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package dev.corruptedark.diditakemymeds.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.siravorona.utils.base.BaseBoundActivity
import dev.corruptedark.diditakemymeds.BuildConfig
import dev.corruptedark.diditakemymeds.R
import dev.corruptedark.diditakemymeds.databinding.ActivityAboutBinding

class AboutActivity : BaseBoundActivity<ActivityAboutBinding>(ActivityAboutBinding::class) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSupportActionBar(binding.appbar.toolbar)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.appbar.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
        binding.viewGithubButton.setOnClickListener {
            val webpage = Uri.parse(getString(R.string.github_link))
            val intent = Intent(Intent.ACTION_VIEW, webpage)
            startActivity(intent)
        }
        binding.supportButton.setOnClickListener {
            if (BuildConfig.BUILD_TYPE == getString(R.string.play_release)) {
                MaterialAlertDialogBuilder(this).setTitle(getString(R.string.sorry))
                        .setMessage(getString(R.string.cannot_donate_explanation))
                        .setNeutralButton(getString(R.string.okay)) { dialog, which ->
                            dialog.dismiss()
                        }.show()
            } else {
                val webpage = Uri.parse(getString(R.string.liberapay_link))
                val intent = Intent(Intent.ACTION_VIEW, webpage)
                startActivity(intent)
            }
        }
        binding.appDescriptionView.text = getString(R.string.app_description,
                BuildConfig.VERSION_NAME)
    }


}