package io.gonative.android.widget

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.core.content.res.ResourcesCompat
import io.gonative.android.R

class HandleView : RelativeLayout {
    private val iconView: ImageView
    private val textView: TextView

    init {
        inflate(context, R.layout.view_handle, this)
        iconView = findViewById(R.id.icon)
        textView = findViewById(R.id.text)
    }

    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyle: Int = 0
    ) : super(context, attrs, defStyle) {
        context.theme.obtainStyledAttributes(attrs, R.styleable.HandleView, 0, 0).apply {
            val backgroundDrawable = getDrawable(R.styleable.HandleView_handleBackground)
                ?: ResourcesCompat.getDrawable(
                    resources,
                    R.drawable.shape_rounded,
                    context.theme
                )
            val iconDrawable = getDrawable(R.styleable.HandleView_iconDrawable)
            val text = getString(R.styleable.HandleView_text)
            val inactiveColor = getColor(R.styleable.HandleView_inactiveColor, inactiveColor)
            val activeColor = getColor(R.styleable.HandleView_activeColor, activeColor)
            initView(backgroundDrawable, iconDrawable, text, inactiveColor, activeColor)
        }
    }

    constructor(
        context: Context,
        backgroundDrawable: Drawable?,
        iconDrawable: Drawable?,
        text: String?,
        @ColorInt inactiveColor: Int,
        @ColorInt activeColor: Int,
    ) : super(context, null, 0) {
        initView(backgroundDrawable, iconDrawable, text, inactiveColor, activeColor)
    }

    var maxTextWidth: Int = Int.MIN_VALUE
    var inactiveColor: Int = Color.WHITE
    var activeColor: Int = Color.WHITE

    fun initView(
        backgroundDrawable: Drawable?,
        iconDrawable: Drawable?,
        text: String?,
        @ColorInt inactiveColor: Int,
        @ColorInt activeColor: Int
    ) {
        background = backgroundDrawable
        iconView.setImageDrawable(iconDrawable)
        setText(text)
        textView.layoutParams.let {
            it.width = 0
            textView.layoutParams = it
        }

        this.inactiveColor = inactiveColor
        this.activeColor = activeColor
        iconView.setColorFilter(inactiveColor)
    }

    fun setText(text: String?) {
        textView.layoutParams.width = LayoutParams.WRAP_CONTENT
        textView.text = text
        textView.measure(
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )
        maxTextWidth = textView.measuredWidth
        textView.layoutParams.let {
            it.width = 0
            textView.layoutParams = it
        }
    }

    fun animateShowText() {
        if (textView.text.isEmpty()) {
            return
        }
        if (textView.layoutParams.width != 0) {
            textView.layoutParams.let {
                it.width = 0
                textView.layoutParams = it
            }
        }
        val animator = ValueAnimator.ofInt(0, maxTextWidth)
        animator.addUpdateListener { anim ->
            val value = anim.animatedValue as Int
            textView.layoutParams.let {
                it.width = value
                textView.layoutParams = it
            }
        }
        animator.duration = 300
        animator.start()
    }

    fun animateHideText() {
        if (textView.text.isEmpty()) {
            return
        }
        val animator = ValueAnimator.ofInt(maxTextWidth, 0)
        animator.duration = 300
        animator.addUpdateListener { anim ->
            val value = anim.animatedValue as Int
            val params = textView.layoutParams
            params.width = value
            textView.layoutParams = params
        }
        animator.start()
    }

    fun animateActive() {
        val animator = ValueAnimator.ofObject(ArgbEvaluator(), inactiveColor, activeColor)
        animator.addUpdateListener { anim ->
            val color = anim.animatedValue as Int
            textView.setTextColor(color)
            iconView.setColorFilter(color, PorterDuff.Mode.SRC_IN)
        }
        animator.duration = 100
        animator.start()
    }

    fun animateInactive() {
        val animator = ValueAnimator.ofObject(ArgbEvaluator(), activeColor, inactiveColor)
        animator.addUpdateListener { anim ->
            val color = anim.animatedValue as Int
            textView.setTextColor(color)
            iconView.setColorFilter(color, PorterDuff.Mode.SRC_IN)
        }
        animator.duration = 200
        animator.start()
    }
}