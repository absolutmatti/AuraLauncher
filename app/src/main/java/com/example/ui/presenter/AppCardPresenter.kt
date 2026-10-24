package com.example.ui.presenter

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import com.example.R
import com.example.model.AppInfo

/**
 * Leanback-Presenter für App-Kacheln auf dem Android TV.
 * Verwaltet das Banner-/Icon-Format (16:9) sowie die geforderte Fokus-Animation:
 * - 1,08x–1,10x Skalierung
 * - Deep Purple Konturrahmen (#9C27B0)
 * - Weicher Übergang in ~180ms
 */
class AppCardPresenter(
    private val onLongClickAction: ((AppInfo, View) -> Unit)? = null
) : Presenter() {

    companion object {
        private const val CARD_WIDTH_DP = 240
        private const val CARD_HEIGHT_DP = 140
        private const val ANIMATION_DURATION_MS = 180L
    }

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val context = parent.context
        val cardView = ImageCardView(context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            cardType = ImageCardView.CARD_TYPE_INFO_UNDER
            setInfoAreaBackgroundColor(ContextCompat.getColor(context, R.color.tv_surface_card))

            val widthPx = dpToPx(context, CARD_WIDTH_DP)
            val heightPx = dpToPx(context, CARD_HEIGHT_DP)
            setMainImageDimensions(widthPx, heightPx)

            // Hintergrund mit Selector für den lila Fokusrahmen
            background = ContextCompat.getDrawable(context, R.drawable.bg_card_selector)

            // Fokus-Animation bei D-Pad-Navigation
            setOnFocusChangeListener { view, hasFocus ->
                animateFocus(view, hasFocus)
            }
        }

        return CardViewHolder(cardView)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val cardView = (viewHolder as CardViewHolder).cardView
        val appInfo = item as? AppInfo ?: return

        cardView.titleText = appInfo.label
        cardView.contentText = if (appInfo.isFavorite) "★ Favorit" else ""

        // Visuelle Darstellung ermitteln (Banner hat Vorrang vor Icon)
        val drawable: Drawable? = appInfo.getDisplayDrawable()

        if (drawable != null) {
            cardView.mainImageView.scaleType = if (appInfo.bannerDrawable != null) {
                ImageView.ScaleType.FIT_CENTER
            } else {
                ImageView.ScaleType.FIT_CENTER
            }
            cardView.mainImage = drawable
        } else {
            cardView.mainImage = ContextCompat.getDrawable(cardView.context, R.drawable.ic_app_default)
        }

        // Lang-Klick für das App-Kontextmenü registrieren
        cardView.setOnLongClickListener { view ->
            onLongClickAction?.invoke(appInfo, view)
            true
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val cardView = (viewHolder as CardViewHolder).cardView
        cardView.badgeImage = null
        cardView.mainImage = null
        cardView.setOnLongClickListener(null)
    }

    /**
     * Führt die synchrone Skalierung und Hervorhebung beim Fokussieren durch.
     */
    private fun animateFocus(view: View, hasFocus: Boolean) {
        val scale = if (hasFocus) 1.08f else 1.0f
        val elevation = if (hasFocus) 12f else 2f

        val scaleX = ObjectAnimator.ofFloat(view, View.SCALE_X, scale)
        val scaleY = ObjectAnimator.ofFloat(view, View.SCALE_Y, scale)
        val elev = ObjectAnimator.ofFloat(view, View.TRANSLATION_Z, elevation)

        AnimatorSet().apply {
            playTogether(scaleX, scaleY, elev)
            duration = ANIMATION_DURATION_MS
            interpolator = DecelerateInterpolator()
            start()
        }
    }

    private fun dpToPx(context: Context, dp: Int): Int {
        val density = context.resources.displayMetrics.density
        return (dp * density).toInt()
    }

    private class CardViewHolder(val cardView: ImageCardView) : ViewHolder(cardView)
}
