package androidx.top.hyperos.dynamic.ext;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.lang.reflect.Field;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class XposedInfo {
    private static final String TAG = "XposedInfo";
    //
    public static Context mContext;
    private static XC_LoadPackage.LoadPackageParam mLpparam;
    // left
    private static RelativeLayout mRLLeft;
    private static FrameLayout mLeftContentWithOutText;
    private static Drawable mLeftIcon;
    private static ImageView mLeftImageView;
    private static TextView mLeftTextView;
    private static TextureView mLeftVideoView;
    // right
    private static RelativeLayout mRLRight;
    private static FrameLayout mRightContentWithOutText;
    private static Drawable mRightIcon;
    private static ImageView mRightImageView;
    private static TextView mRightTextView;
    private static TextureView mRightVideoView;
    private static LinearLayout mDarkToast;
    private static ViewGroup mDarkToastContent;
    private static int mScreenHeight;
    private static int mScreenWidth;
    private static View mStrongToastBottomView;
    private static View mView;
    private static View mCutOut;
    private static XSharedPreferences sp;

    public static XC_LoadPackage.LoadPackageParam getLoad() {
        if (mLpparam != null) {
            return mLpparam;
        }
        return null;
    }

    public static void setLoad(XC_LoadPackage.LoadPackageParam lpparam) {
        mLpparam = lpparam;
    }

    public static Class<?> getClass(XC_LoadPackage.LoadPackageParam lpparam, String classname) {
        try {
            return lpparam.classLoader.loadClass(classname);
        } catch (ClassNotFoundException e) {
            XposedBridge.log(e);
            e.printStackTrace();
            return null;
        }
    }

    public static Class<?> findMIUIStrongToast() {
        return getClass(getLoad(), Config.StrongToastPackage);
    }

    public static Class<?> findStrongToastModel() {
        return getClass(getLoad(), Config.StrongToastModelPackage);
    }

    public static void init() {
        NotifyHelper.init();
        XposedInfo.setDuration(4000);
        sp = Tools.getSharedPreferences("data");
        if (sp != null) {
            if (sp.getBoolean("hide", false)) {
                reOnPreDrawListener();
            }
            if (sp.getBoolean("edit", false)) {
                customStyleToast();
            }
        }
    }

    public static void customStyleToast() {
        if (sp.getBoolean("is14", false)) {
            XposedHelpers.findAndHookConstructor(
                    findMIUIStrongToast(),
                    Context.class,
                    AttributeSet.class,
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            getArgs(param);
                        }
                    });
        } else {
            XposedHelpers.findAndHookMethod(
                    findMIUIStrongToast(),
                    "showCustomStrongToast",
                    findStrongToastModel(),
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        }
                    });
        }
    }

    public static void getArgs(XC_MethodHook.MethodHookParam param) {
        // left
        mRLLeft = (RelativeLayout) XposedHelpers.getObjectField(param.thisObject, "mRLLeft");
        mLeftContentWithOutText = (FrameLayout) XposedHelpers.getObjectField(param.thisObject, "mLeftContentWithOutText");
        mLeftIcon = (Drawable) XposedHelpers.getObjectField(param.thisObject, "mLeftIcon");
        mLeftImageView = (ImageView) XposedHelpers.getObjectField(param.thisObject, "mLeftImageView");
        mLeftTextView = (TextView) XposedHelpers.getObjectField(param.thisObject, "mLeftTextView");
        mLeftVideoView = (TextureView) XposedHelpers.getObjectField(param.thisObject, "mLeftVideoView");
        // right
        mRLRight = (RelativeLayout) XposedHelpers.getObjectField(param.thisObject, "mRLRight");
        mRightContentWithOutText = (FrameLayout) XposedHelpers.getObjectField(param.thisObject, "mRightContentWithOutText");
        mRightIcon = (Drawable) XposedHelpers.getObjectField(param.thisObject, "mRightIcon");
        mRightImageView = (ImageView) XposedHelpers.getObjectField(param.thisObject, "mRightImageView");
        mRightTextView = (TextView) XposedHelpers.getObjectField(param.thisObject, "mRightTextView");
        mRightVideoView = (TextureView) XposedHelpers.getObjectField(param.thisObject, "mRightVideoView");
        mContext = (Context) XposedHelpers.getObjectField(param.thisObject, "mContext");
        mView = (View) XposedHelpers.getObjectField(param.thisObject, "mView");
        mCutOut = (View) XposedHelpers.getObjectField(param.thisObject, "mCutOut");
        // mWindowManager = (WindowManager) XposedHelpers.getObjectField(param.thisObject, "mWindowManager");
        mDarkToast = (LinearLayout) XposedHelpers.getObjectField(param.thisObject, "mDarkToast");
        mDarkToastContent = (ViewGroup) XposedHelpers.getObjectField(param.thisObject, "mDarkToastContent");
        mStrongToastBottomView = (ImageView) XposedHelpers.getObjectField(param.thisObject, "mStrongToastBottomView");
        mScreenWidth = (int) XposedHelpers.getObjectField(param.thisObject, "mScreenWidth");
        mScreenHeight = (int) XposedHelpers.getObjectField(param.thisObject, "mScreenHeight");
        XposedHelpers.setBooleanField(param.thisObject, "mShowBottom", false);
        setWindowAnimationStyle(0);
        //checkInit();
        mStrongToastBottomView.setVisibility(View.GONE);
        //mRightTextView.setVisibility(View.GONE);
        initDarkToast();
        initDarkToastContent();
        unrollAnimations();
        initsetValue();
        reTextSize();
        reConstraint();
    }

    public static void reOnPreDrawListener() {
        XposedHelpers.findAndHookMethod(
                findMIUIStrongToast(),
                "onAttachedToWindow",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                        final Object thisObject = param.thisObject;
                        final FrameLayout toast = (FrameLayout) thisObject;
                        toast.getViewTreeObserver()
                                .addOnPreDrawListener(
                                        new ViewTreeObserver.OnPreDrawListener() {
                                            @Override
                                            public boolean onPreDraw() {
                                                // XposedHelpers.setBooleanField(thisObject, "mCheckStartAnimation", true);
                                                // toast.getViewTreeObserver().removeOnPreDrawListener(this);
                                                return false;
                                            }
                                        });
                    }
                });
    }

    private static void reTextSize() {
        mRightTextView.setTextSize(16);
        mLeftTextView.setTextSize(16);
    }

    public static void unrollAnimations() {
        int targetWidth = (int) (mScreenWidth / 2);
        ValueAnimator animator = ValueAnimator.ofInt(getMixWith(), targetWidth);
        animator.setDuration(650);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                int value = (int) animation.getAnimatedValue();
                mDarkToastContent.getLayoutParams().width = value;
                mDarkToastContent.requestLayout();
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
            }
        });
        animator.start();
    }

    public static void reConstraint() {
        //int ToastWidth = mDarkToastContent.getWidth();
        //mRightTextView.setVisibility(View.VISIBLE);
        ViewGroup.LayoutParams layoutParams = mRLRight.getLayoutParams();
        layoutParams.width = mRLLeft.getWidth();
        mRLRight.setLayoutParams(layoutParams);
        mRLLeft.setPaddingRelative(25, 0, 0, 0);
        mLeftVideoView.setPaddingRelative(0, 0, 0, 15);
        mRLRight.setPaddingRelative(0, 0, 25, 0);
        mDarkToastContent.requestLayout();
    }

    public static int getMixWith() {
        return (int) (mScreenWidth / 3.5);
    }

    private static void initDarkToast() {
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        layoutParams.gravity = Gravity.CENTER;
        layoutParams.setMargins(25, 5, 25, 5);
        mDarkToast.setLayoutParams(layoutParams);
        mDarkToast.requestLayout();
    }

    private static void initDarkToastContent() {
        // LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams((int) (mScreenWidth / 3.5), ViewGroup.LayoutParams.MATCH_PARENT);
        LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) mDarkToastContent.getLayoutParams();
        layoutParams.width = getMixWith();
        layoutParams.gravity = Gravity.CENTER;
        mDarkToastContent.setLayoutParams(layoutParams);
        mDarkToastContent.setBackgroundDrawable(Tools.getShepeBackground(Config.black, 90));
        /*
        mDarkToastContent.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (isEnd) {
                            isEnd = false;
                            unrollLayout(600);
                        }
                    }
                });

         */
        mDarkToastContent.requestLayout();
    }

    private static void initsetValue() {
        XposedHelpers.findAndHookMethod(
                findMIUIStrongToast(),
                "setValue",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        String mCurrentToastCategory = (String) XposedHelpers.getObjectField(param.thisObject, "mCurrentToastCategory");
                        mRLLeft = (RelativeLayout) XposedHelpers.getObjectField(param.thisObject, "mRLLeft");
                        mLeftContentWithOutText = (FrameLayout) XposedHelpers.getObjectField(param.thisObject, "mLeftContentWithOutText");
                        mLeftIcon = (Drawable) XposedHelpers.getObjectField(param.thisObject, "mLeftIcon");
                        mLeftImageView = (ImageView) XposedHelpers.getObjectField(param.thisObject, "mLeftImageView");
                        mLeftTextView = (TextView) XposedHelpers.getObjectField(param.thisObject, "mLeftTextView");
                        mLeftVideoView = (TextureView) XposedHelpers.getObjectField(param.thisObject, "mLeftVideoView");
                        // right
                        mRLRight = (RelativeLayout) XposedHelpers.getObjectField(param.thisObject, "mRLRight");
                        mRightContentWithOutText = (FrameLayout) XposedHelpers.getObjectField(param.thisObject, "mRightContentWithOutText");
                        mRightIcon = (Drawable) XposedHelpers.getObjectField(param.thisObject, "mRightIcon");
                        mRightImageView = (ImageView) XposedHelpers.getObjectField(param.thisObject, "mRightImageView");
                        mRightTextView = (TextView) XposedHelpers.getObjectField(param.thisObject, "mRightTextView");
                        mRightVideoView = (TextureView) XposedHelpers.getObjectField(param.thisObject, "mRightVideoView");
                        //mRightTextView.setVisibility(View.GONE);
                        if (mCurrentToastCategory.equals("charge")) {
                        }
                    }
                });
    }

    public static void checkInit() {
        Object[] table = new Object[]{mRLLeft, mRLRight, mScreenHeight, mScreenWidth, mDarkToast, mDarkToastContent, mCutOut, mView, mContext, mStrongToastBottomView, mLeftContentWithOutText, mLeftIcon, mLeftImageView, mLeftTextView, mRightContentWithOutText, mRightVideoView, mRightIcon, mRightImageView, mRightTextView};
        Field[] fields = XposedInfo.class.getDeclaredFields();
        for (Field field : fields) {
            try {
                field.setAccessible(true);
                Object value = field.get(null);
                if (value == null) {
                    String fieldName = field.getName();
                    XposedBridge.log(Tools.concat(fieldName, " 为 null"));
                }
            } catch (IllegalAccessException e) {
                XposedBridge.log(Tools.concat("异常", e.toString()));
                for (Object i : table) {
                    if (i == null) {
                        String fieldName = field.getName();
                        XposedBridge.log(fieldName + " 为 null");
                    }
                }
            }
        }
    }

    public static void setDuration(long time) {
        XposedHelpers.findAndHookMethod(findStrongToastModel(), "getDuration", XC_MethodReplacement.returnConstant(time));
    }

    public static void setWindowAnimationStyle(final int anim) {
        XposedHelpers.findAndHookMethod(
                findMIUIStrongToast(),
                "getWindowParam",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        //WindowManager.LayoutParams window = (WindowManager.LayoutParams) param.getResult();
                        //if (window != null) {
                        // window.windowAnimations = anim;

                        // window.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
                        // window.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR;
                        param.setResult(Tools.getWindowParams());
                        // }
                    }
                });
    }
}
