package com.example.newapp;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayDeque;
import java.util.WeakHashMap;

/**
 * Single owner of in-Activity page navigation.
 * Screens may render pages, but must not maintain their own navigation stacks.
 */
final class PosNavigation {
    private static final WeakHashMap<Activity, ArrayDeque<View>> STACKS = new WeakHashMap<>();

    private PosNavigation() {}

    static void showRoot(Activity activity, View view) {
        if (activity == null || view == null) return;
        STACKS.remove(activity);
        activity.setContentView(view);
    }

    static void showPage(Activity activity, View view) {
        if (activity == null || view == null) return;
        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content != null && content.getChildCount() > 0) {
            View previous = content.getChildAt(0);
            if (previous != view) {
                ArrayDeque<View> stack = STACKS.get(activity);
                if (stack == null) {
                    stack = new ArrayDeque<>();
                    STACKS.put(activity, stack);
                }
                stack.push(previous);
            }
        }
        activity.setContentView(view);
    }

    static void back(Activity activity) {
        if (activity == null) return;
        ArrayDeque<View> stack = STACKS.get(activity);
        if (stack != null && !stack.isEmpty()) {
            View previous = stack.pop();
            activity.setContentView(previous);
            if (stack.isEmpty()) STACKS.remove(activity);
        } else {
            activity.finish();
        }
    }

    static void clear(Activity activity) {
        if (activity != null) STACKS.remove(activity);
    }
}
