package com.example.opsc.clubs

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.opsc.R
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.R as MaterialR

/** The user's clubs. Used as a list on the Clubs tab and as small horizontal cards on the dashboard. */
class ClubAdapter(@LayoutRes private val layout: Int, private val onClick: (Club) -> Unit) : ListAdapter<Club, ClubAdapter.Holder>(Diff) {

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val name: TextView = view.findViewById(R.id.clubName)
        private val meta: TextView = view.findViewById(R.id.clubMeta)
        private val description: TextView? = view.findViewById(R.id.clubDescription)

        fun bind(club: Club, onClick: (Club) -> Unit) {
            val context = itemView.context
            name.text = club.name
            val state = context.getString(if (club.isOpen) R.string.club_status_open else R.string.club_status_active)
            meta.text = context.getString(R.string.club_meta, club.memberIds.size, state)
            description?.text = club.description
            description?.visibility = if (club.description.isBlank()) View.GONE else View.VISIBLE
            itemView.setOnClickListener { onClick(club) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(LayoutInflater.from(parent.context).inflate(layout, parent, false))
    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position), onClick)

    private object Diff : DiffUtil.ItemCallback<Club>() {
        override fun areItemsTheSame(a: Club, b: Club) = a.id == b.id
        override fun areContentsTheSame(a: Club, b: Club) = a == b
    }
}

/** The league table rows below the podium. */
class StandingAdapter : ListAdapter<RankedRow, StandingAdapter.Holder>(Diff) {

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val rank: TextView = view.findViewById(R.id.standingRank)
        private val name: TextView = view.findViewById(R.id.standingName)
        private val stats: TextView = view.findViewById(R.id.standingStats)
        private val points: TextView = view.findViewById(R.id.standingPoints)

        fun bind(item: RankedRow) {
            val context = itemView.context
            rank.text = item.rank.toString()
            name.text = if (item.isMe) context.getString(R.string.name_you, item.name) else item.name
            stats.text = context.getString(R.string.standing_stats, item.row.played, item.row.won, item.row.drawn, item.row.lost)
            points.text = item.row.points.toString()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_standing, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    private object Diff : DiffUtil.ItemCallback<RankedRow>() {
        override fun areItemsTheSame(a: RankedRow, b: RankedRow) = a.row.uid == b.row.uid
        override fun areContentsTheSame(a: RankedRow, b: RankedRow) = a == b
    }
}

/** A list entry on the Fixtures tab: a round title or a match. */
sealed interface FixtureItem {
    data class Header(val round: Int) : FixtureItem
    data class Match(val view: FixtureView) : FixtureItem
}

/** Fixtures grouped by round (1 to 3) with a status badge on each. Only the referee can open an unfinished match. */
class FixtureAdapter(private val onReferee: (FixtureView) -> Unit) : ListAdapter<FixtureItem, RecyclerView.ViewHolder>(Diff) {

    class HeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bind(item: FixtureItem.Header) {
            (itemView as TextView).text = itemView.context.getString(R.string.round_title, item.round)
        }
    }

    class MatchHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.fixtureTitle)
        private val status: TextView = view.findViewById(R.id.fixtureStatus)
        private val score: TextView = view.findViewById(R.id.fixtureScore)
        private val referee: TextView = view.findViewById(R.id.fixtureReferee)
        private val action: TextView = view.findViewById(R.id.fixtureAction)

        fun bind(item: FixtureView, onReferee: (FixtureView) -> Unit) {
            val context = itemView.context
            val fixture = item.fixture
            title.text = context.getString(R.string.fixture_title, item.player1Name, item.player2Name)
            referee.text = context.getString(R.string.fixture_referee, item.refereeName)
            val verified = fixture.status == FixtureStatus.VERIFIED
            score.text = if (verified) "${fixture.player1Score} – ${fixture.player2Score}" else "–"

            status.setText(
                when (fixture.status) {
                    FixtureStatus.SCHEDULED -> R.string.status_scheduled
                    FixtureStatus.PENDING -> R.string.status_pending
                    FixtureStatus.VERIFIED -> R.string.status_verified
                }
            )
            val (backgroundAttr, textAttr) = when (fixture.status) {
                FixtureStatus.VERIFIED -> R.attr.colorSuccessContainer to R.attr.colorOnSuccessContainer
                FixtureStatus.PENDING -> MaterialR.attr.colorPrimaryContainer to MaterialR.attr.colorOnPrimaryContainer
                FixtureStatus.SCHEDULED -> MaterialR.attr.colorSecondaryContainer to MaterialR.attr.colorOnSecondaryContainer
            }
            status.backgroundTintList = ColorStateList.valueOf(MaterialColors.getColor(itemView, backgroundAttr))
            status.setTextColor(MaterialColors.getColor(itemView, textAttr))

            action.visibility = if (item.canReferee) View.VISIBLE else View.GONE
            val card = itemView as MaterialCardView
            card.isClickable = item.canReferee
            card.isFocusable = item.canReferee
            card.setOnClickListener(if (item.canReferee) View.OnClickListener { onReferee(item) } else null)
        }
    }

    override fun getItemViewType(position: Int) = if (getItem(position) is FixtureItem.Header) TYPE_HEADER else TYPE_MATCH

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) HeaderHolder(inflater.inflate(R.layout.item_round_header, parent, false))
        else MatchHolder(inflater.inflate(R.layout.item_fixture, parent, false))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is FixtureItem.Header -> (holder as HeaderHolder).bind(item)
            is FixtureItem.Match -> (holder as MatchHolder).bind(item.view, onReferee)
        }
    }

    private object Diff : DiffUtil.ItemCallback<FixtureItem>() {
        override fun areItemsTheSame(a: FixtureItem, b: FixtureItem) = when {
            a is FixtureItem.Header && b is FixtureItem.Header -> a.round == b.round
            a is FixtureItem.Match && b is FixtureItem.Match -> a.view.fixture.id == b.view.fixture.id
            else -> false
        }
        override fun areContentsTheSame(a: FixtureItem, b: FixtureItem) = a == b
    }

    private companion object {
        const val TYPE_HEADER = 0
        const val TYPE_MATCH = 1
    }
}

/** Groups fixtures into "Round 1", "Round 2", "Round 3" sections. */
fun groupByRound(fixtures: List<FixtureView>): List<FixtureItem> =
    fixtures.groupBy { it.fixture.round }.toSortedMap().flatMap { (round, list) ->
        listOf<FixtureItem>(FixtureItem.Header(round)) + list.map { FixtureItem.Match(it) }
    }

/** The member list. */
class MemberAdapter : ListAdapter<MemberView, MemberAdapter.Holder>(Diff) {

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val name: TextView = view.findViewById(R.id.memberName)
        private val role: TextView = view.findViewById(R.id.memberRole)

        fun bind(member: MemberView) {
            val context = itemView.context
            name.text = if (member.isMe) context.getString(R.string.name_you, member.name) else member.name
            role.text = if (member.isOwner) context.getString(R.string.member_owner) else ""
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_member, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    private object Diff : DiffUtil.ItemCallback<MemberView>() {
        override fun areItemsTheSame(a: MemberView, b: MemberView) = a.uid == b.uid
        override fun areContentsTheSame(a: MemberView, b: MemberView) = a == b
    }
}
