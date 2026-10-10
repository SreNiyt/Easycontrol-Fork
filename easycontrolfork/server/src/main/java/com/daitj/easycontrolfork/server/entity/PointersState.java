package com.daitj.easycontrolfork.server.entity;

import android.util.SparseArray;
import android.view.MotionEvent;

public final class PointersState {
    private static final int MAX_POINTERS = 10;

    private final SparseArray<Pointer> pointersById = new SparseArray<>(MAX_POINTERS);
    private final Pointer[] pointersByLocalId = new Pointer[MAX_POINTERS];

    public final MotionEvent.PointerProperties[] pointerProperties = new MotionEvent.PointerProperties[MAX_POINTERS];
    public final MotionEvent.PointerCoords[] pointerCoords = new MotionEvent.PointerCoords[MAX_POINTERS];

    public PointersState() {
        for (int i = 0; i < MAX_POINTERS; ++i) {
            MotionEvent.PointerProperties props = new MotionEvent.PointerProperties();
            props.toolType = MotionEvent.TOOL_TYPE_FINGER;
            pointerProperties[i] = props;

            MotionEvent.PointerCoords coords = new MotionEvent.PointerCoords();
            coords.orientation = 0;
            coords.size = 0.01f;
            coords.pressure = 1f;
            pointerCoords[i] = coords;
        }
    }

    public synchronized Pointer newPointer(int pointerId, long now) {
        if (pointersById.size() >= MAX_POINTERS) return null;

        for (int i = 0; i < MAX_POINTERS; i++) {
            if (pointersByLocalId[i] == null) {
                Pointer pointer = new Pointer(i, now);
                pointersByLocalId[i] = pointer;
                pointersById.put(pointerId, pointer);
                return pointer;
            }
        }

        return null;
    }

    public synchronized Pointer get(int pointerId) {
        return pointersById.get(pointerId);
    }

    public synchronized void remove(int pointerId) {
        Pointer pointer = pointersById.get(pointerId);
        if (pointer != null) {
            pointersById.remove(pointerId);
            pointersByLocalId[pointer.id] = null;
        }
    }

    public synchronized int update() {
        int activeIndex = 0;
        for (int i = 0; i < MAX_POINTERS; i++) {
            Pointer pointer = pointersByLocalId[i];
            if (pointer != null) {
                pointerProperties[activeIndex].id = pointer.id;
                pointerCoords[activeIndex].x = pointer.x;
                pointerCoords[activeIndex].y = pointer.y;
                activeIndex++;
            }
        }

        return activeIndex;
    }

    public synchronized int getPointerIndex(int localPointerId) {
        if (localPointerId < 0 || localPointerId >= MAX_POINTERS || pointersByLocalId[localPointerId] == null) {
            return -1;
        }

        int activeIndex = 0;
        for (int i = 0; i < localPointerId; i++) {
            if (pointersByLocalId[i] != null) {
                activeIndex++;
            }
        }
        
        return activeIndex;
    }
}

