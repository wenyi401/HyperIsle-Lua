package androidx.top.hyperos.dynamic.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

public class ArtActionBar extends LinearLayout {
    public ArtActionBar(Context context) {
        this(context, null);
    }

    public ArtActionBar(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ArtActionBar(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public ArtActionBar(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    private void init() {
        setOrientation(HORIZONTAL);
    }

    public void setStatusBarHeight() {

    }
}
