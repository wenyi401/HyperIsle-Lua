package androidx.top.hyperos.dynamic.ext;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.drawable.Icon;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * @Author 文艺 or literature and art
 * @Date 2023/11/23 18:06
 */

public class HyperContentToast extends FrameLayout {
    private View mContentView;
    private TextView mTextView;
    private TextView mTitleView;
    private ImageView mIconView;
    private Context mContext;
    private LinearLayout layout;
    private LinearLayout textLayout;
    private WindowManager mWindowManager;
    private int mCurrentHeight;

    public HyperContentToast(Context context) {
        super(context);
        this.mContext = context;
        this.mWindowManager = (WindowManager) context.getSystemService(WindowManager.class);
        this.mContentView = createContentView(context);
        this.layout = mContentView.findViewWithTag("layout");
        this.textLayout = mContentView.findViewWithTag("textLayout");
        this.mTextView = mContentView.findViewWithTag("text");
        this.mTitleView = mContentView.findViewWithTag("title");
        this.mIconView = mContentView.findViewWithTag("icon");

        unrollAnimations();

        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {

            @Override
            public void run() {
                removeView();
            }
        }, 4000);
    }

    public void showTextToast(Icon icon, String text) {
        if (text == null) {
            return;
        }
        //mTextView.setText(text);
        mIconView.setImageIcon(icon);


        String[] splitMessage = text.split(":");

        mTextView.setText(splitMessage[0].trim());
        mTitleView.setText(splitMessage[1].trim());
        //Toast.makeText(mContext, text, Toast.LENGTH_SHORT).show();

        mWindowManager.addView(mContentView, Tools.getWindowParams());
        unrollAnimations();
    }

    private View createContentView(Context context) {
        this.mCurrentHeight = (int) (0.0969f * Tools.getWidth(context));
        int dp5 = Tools.dp(context, 5);
        final LinearLayout contentView = new LinearLayout(context);
        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.FILL_PARENT,
                LinearLayout.LayoutParams.FILL_PARENT
        );
        contentParams.gravity = Gravity.CENTER;
        contentView.setLayoutParams(contentParams);
        contentView.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                getMixWidth(context),
                LayoutParams.MATCH_PARENT
        );

        layoutParams.height = this.mCurrentHeight;
        layoutParams.gravity = Gravity.CENTER;
        layoutParams.setMargins(25, 5, 25, 5);
        LinearLayout layout = new LinearLayout(context);
        layout.setLayoutParams(layoutParams);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setBackgroundDrawable(Tools.getShepeBackground(Config.black, 90));
        layout.setTag("layout");
        ImageView icon = new ImageView(context);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        iconParams.setMargins(dp5, dp5, dp5, dp5);
        iconParams.gravity = Gravity.CENTER;
        icon.setLayoutParams(iconParams);
        icon.setTag("icon");

        LinearLayout.LayoutParams textLayoutParams = new LinearLayout.LayoutParams(
                layoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
        );

        textLayoutParams.height = this.mCurrentHeight;
        textLayoutParams.setMargins(25, 5, 25, 5);
        final LinearLayout textLayout = new LinearLayout(context);
        textLayout.setLayoutParams(textLayoutParams);
        textLayout.setOrientation(LinearLayout.HORIZONTAL);
        textLayout.setBackgroundDrawable(Tools.getShepeBackground(Config.black, 90));
        textLayout.setTag("textLayout");
        TextView text = new TextView(context);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        textParams.gravity = Gravity.CENTER;
        text.setLayoutParams(textParams);
        text.setTag("text");

        TextView title = new TextView(context);
        title.setLayoutParams(textParams);
        title.setTag("title");

        textLayout.setVisibility(View.GONE);
        layout.addView(icon);
        textLayout.addView(text);
        textLayout.addView(title);
        contentView.addView(layout);
        contentView.addView(textLayout);
        contentView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                unrollLayout(1000);
            }
        });

        return contentView;
    }

    private void unrollAnimations() {
        int initialWidth = Tools.dp(mContext, 5); // 获取 View 的初始宽度
        int targetWidth = getMixWidth(mContext);
        ; // 设置目标宽度
        // 定义从初始状态到目标状态的动画
        ValueAnimator animator = ValueAnimator.ofInt(initialWidth, targetWidth);
        animator.setDuration(500); // 动画持续时间为1秒
        animator.setInterpolator(new AccelerateDecelerateInterpolator()); // 设置动画插值器
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                int value = (int) animation.getAnimatedValue();
                mContentView.getLayoutParams().width = value; // 改变宽度
                mContentView.requestLayout(); // 通知 UI 更新布局
            }
        });
        animator.start();
    }

    private void unrollLayout(int time) {
        int width = mContentView.getWidth();
        int width2 = getMixWidth(mContext);

        ObjectAnimator ofPropertyValuesHolder = ObjectAnimator.ofPropertyValuesHolder(mContentView, PropertyValuesHolder.ofInt("right", width, width2), PropertyValuesHolder.ofInt("left", width, width2));
        ofPropertyValuesHolder.setDuration(time);
        ofPropertyValuesHolder.setInterpolator(new DecelerateInterpolator(2.5f));
        ofPropertyValuesHolder.addUpdateListener(
                new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator valueAnimator) {
                        int intValue = ((Integer) valueAnimator.getAnimatedValue()).intValue();
                        ViewGroup.LayoutParams layoutParams = mContentView.getLayoutParams();
                        layoutParams.width = intValue;
                        mContentView.setLayoutParams(layoutParams);
                        mContentView.requestLayout();
                    }
                });
        ofPropertyValuesHolder.start();
    }

    public void removeView() {
        if (mWindowManager != null && mContentView != null) {
            mWindowManager.removeView(mContentView);
        }
    }

    private int getMixWidth(Context context) {
        return (int) (Tools.getWidth(context) / 3.5);
    }
}
