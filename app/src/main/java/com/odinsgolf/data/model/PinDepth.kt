package com.odinsgolf.data.model

/**
 * Which part of the green today's pin is on. Drives the Distance screen's hero yardage
 * so the big number reflects the pin, not just the centre — front↔back can be a club or
 * two on these greens. Held in memory for the outing (resets to MIDDLE on relaunch);
 * MIDDLE == green centre, the app's original behaviour.
 */
enum class PinDepth { FRONT, MIDDLE, BACK }
