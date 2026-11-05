package com.example.opsc.clubs

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import coil3.load
import com.example.opsc.R
import com.example.opsc.media.MediaRepository
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.CircularProgressIndicator
import kotlinx.coroutines.launch

/** Shows the proof photo a referee attached to a fixture. */
class ProofDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val content = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_proof, null)
        val progress = content.findViewById<CircularProgressIndicator>(R.id.proofProgress)
        val image = content.findViewById<ImageView>(R.id.proofImage)
        val error = content.findViewById<View>(R.id.proofError)
        val clubId = requireArguments().getString(ARG_CLUB).orEmpty()
        val fixtureId = requireArguments().getString(ARG_FIXTURE).orEmpty()

        lifecycleScope.launch {
            // The proof is saved once and never changes, so version 1 is right.
            val file = MediaRepository.get(requireContext()).ensureLocal(MediaRepository.proofPath(clubId, fixtureId), 1L)
            progress.visibility = View.GONE
            if (file == null) {
                error.visibility = View.VISIBLE
            } else {
                image.visibility = View.VISIBLE
                image.load(file)
            }
        }
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.proof_title)
            .setView(content)
            .setPositiveButton(R.string.dialog_close, null)
            .create()
    }

    companion object {
        private const val ARG_CLUB = "clubId"
        private const val ARG_FIXTURE = "fixtureId"

        fun newInstance(clubId: String, fixtureId: String) = ProofDialogFragment().apply {
            arguments = bundleOf(ARG_CLUB to clubId, ARG_FIXTURE to fixtureId)
        }
    }
}
