package com.example.opsc.notifications

import android.app.Application
import android.content.Context
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.opsc.R
import com.example.opsc.ui.StateLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.WriteBatch
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch

/** In-app notifications at users/{uid}/notifications/{id}. They update live. Other users' phones create them, because the free Firebase plan has no server. */
class NotificationRepository(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {
    private fun collection(uid: String) = db.collection("users").document(uid).collection("notifications")

    fun observe(uid: String): Flow<List<InAppNotification>> = callbackFlow {
        val registration = collection(uid).addSnapshotListener { snapshot, error ->
            if (error != null) { close(error); return@addSnapshotListener }
            trySend(NotificationLogic.sortNewestFirst(snapshot?.documents.orEmpty().mapNotNull { it.toNotification() }))
        }
        awaitClose { registration.remove() }
    }

    fun markRead(uid: String, id: String) {
        collection(uid).document(id).update("read", true)
    }

    fun markAllRead(uid: String, ids: List<String>) {
        ids.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.update(collection(uid).document(it), "read", true) }
            batch.commit()
        }
    }

    /** Adds a notification for one person to a batch. */
    fun queue(batch: WriteBatch, recipientUid: String, fromUid: String, type: NotificationType, clubId: String, clubName: String, args: List<String>) {
        val ref: DocumentReference = collection(recipientUid).document()
        batch.set(
            ref,
            mapOf(
                "fromUid" to fromUid, "type" to type.wire, "clubId" to clubId, "clubName" to clubName,
                "args" to args, "read" to false, "createdAt" to FieldValue.serverTimestamp()
            )
        )
    }

    private fun DocumentSnapshot.toNotification(): InAppNotification? {
        val type = NotificationType.fromWire(getString("type")) ?: return null
        @Suppress("UNCHECKED_CAST")
        val args = (get("args") as? List<Any?>).orEmpty().map { it?.toString().orEmpty() }
        return InAppNotification(
            id = id, type = type, clubId = getString("clubId").orEmpty(), clubName = getString("clubName").orEmpty(), args = args,
            read = getBoolean("read") ?: false,
            createdAt = getTimestamp("createdAt", ServerTimestampBehavior.ESTIMATE)?.toDate()?.time ?: 0L
        )
    }
}

/** Turns a stored notification into text in the phone's language. */
object NotificationRenderer {
    private fun arg(n: InAppNotification, index: Int) = n.args.getOrElse(index) { "" }

    fun title(context: Context, n: InAppNotification): String = context.getString(
        when (n.type) {
            NotificationType.FIXTURES_SCHEDULED -> R.string.notif_fixtures_title
            NotificationType.REFEREE_ASSIGNED -> R.string.notif_referee_title
            NotificationType.RESULT_VERIFIED -> R.string.notif_result_title
            NotificationType.LEADERBOARD_CHANGED -> R.string.notif_leaderboard_title
        }
    )

    fun body(context: Context, n: InAppNotification): String = when (n.type) {
        NotificationType.FIXTURES_SCHEDULED -> context.getString(R.string.notif_fixtures_body, n.clubName, arg(n, 0))
        NotificationType.REFEREE_ASSIGNED -> context.getString(R.string.notif_referee_body, n.clubName, arg(n, 0))
        NotificationType.RESULT_VERIFIED -> context.getString(R.string.notif_result_body, n.clubName, arg(n, 0), arg(n, 1), arg(n, 2), arg(n, 3))
        NotificationType.LEADERBOARD_CHANGED -> context.getString(R.string.notif_leaderboard_body, n.clubName)
    }
}

/** What the notification list and bell badge show. */
data class NotificationsUiState(val loading: Boolean = true, val error: Boolean = false, val items: List<InAppNotification> = emptyList()) {
    val unread: Int get() = NotificationLogic.unreadCount(items)
}

/** Live notifications for the signed-in user, shared by the badge and the list. */
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = NotificationRepository()
    private val uid: String? = FirebaseAuth.getInstance().currentUser?.uid
    private val retry = MutableStateFlow(0)

    val state: StateFlow<NotificationsUiState> = retry.flatMapLatest {
        if (uid == null) flowOf(NotificationsUiState(loading = false))
        else repository.observe(uid)
            .map { NotificationsUiState(loading = false, items = it) }
            .catch { emit(NotificationsUiState(loading = false, error = true)) }
            .onStart { emit(NotificationsUiState(loading = true)) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, NotificationsUiState())

    fun retry() {
        retry.value += 1
    }

    fun markRead(id: String) {
        uid?.let { repository.markRead(it, id) }
    }

    fun markAllRead() {
        val owner = uid ?: return
        repository.markAllRead(owner, state.value.items.filter { !it.read }.map { it.id })
    }
}

/** The notification rows. */
class NotificationAdapter(private val onClick: (InAppNotification) -> Unit) : ListAdapter<InAppNotification, NotificationAdapter.Holder>(Diff) {

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.notifTitle)
        private val body: TextView = view.findViewById(R.id.notifBody)
        private val time: TextView = view.findViewById(R.id.notifTime)
        private val dot: View = view.findViewById(R.id.notifUnreadDot)

        fun bind(item: InAppNotification, onClick: (InAppNotification) -> Unit) {
            val context = itemView.context
            title.text = NotificationRenderer.title(context, item)
            body.text = NotificationRenderer.body(context, item)
            time.text = if (item.createdAt == 0L) "" else DateUtils.getRelativeTimeSpanString(item.createdAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
            dot.visibility = if (item.read) View.INVISIBLE else View.VISIBLE
            itemView.contentDescription = (if (item.read) "" else context.getString(R.string.notif_unread) + ". ") + "${title.text}. ${body.text}"
            itemView.setOnClickListener { onClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_notification, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position), onClick)

    private object Diff : DiffUtil.ItemCallback<InAppNotification>() {
        override fun areItemsTheSame(a: InAppNotification, b: InAppNotification) = a.id == b.id
        override fun areContentsTheSame(a: InAppNotification, b: InAppNotification) = a == b
    }
}

/** The notifications screen. */
class NotificationsFragment : Fragment(R.layout.fragment_notifications) {
    private val viewModel: NotificationsViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val stateLayout = view.findViewById<StateLayout>(R.id.notifState)
        val list = view.findViewById<RecyclerView>(R.id.notifList)
        val markAll = view.findViewById<MaterialButton>(R.id.notifMarkAll)
        val adapter = NotificationAdapter { item ->
            viewModel.markRead(item.id)
            if (item.clubId.isNotEmpty()) findNavController().navigate(R.id.clubDetailFragment, bundleOf("clubId" to item.clubId))
        }
        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = adapter
        markAll.setOnClickListener { viewModel.markAllRead() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    markAll.visibility = if (state.unread > 0) View.VISIBLE else View.GONE
                    when {
                        state.loading -> stateLayout.showLoading()
                        state.error -> stateLayout.showError(R.string.notif_load_error) { viewModel.retry() }
                        state.items.isEmpty() -> stateLayout.showEmpty(R.drawable.ic_bell, R.string.notif_empty)
                        else -> {
                            adapter.submitList(state.items)
                            stateLayout.showContent()
                        }
                    }
                }
            }
        }
    }
}
