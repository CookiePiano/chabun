package com.syncdesk.app.domain

/**
 * Feeds [events] to the engine the way the app does, with a vehicle that carries out every
 * command at once and reports the result. Tests then read [SyncDeskState.cabin] as the cabin the
 * driver would actually be sitting in.
 */
internal fun SyncDeskState.afterEvents(vararg events: SyncDeskEvent): SyncDeskState =
    events.fold(this) { state, event ->
        val next = SyncDeskEngine.reduce(state, event)
        val command = next.command
        if (command != null && command.id != state.command?.id) {
            SyncDeskEngine.reduce(next, SyncDeskEvent.CabinReported(next.cabin.applying(command.change)))
        } else {
            next
        }
    }

/** One office step as the app performs it: the actuator moves, the vehicle reports, the step is done. */
internal fun SyncDeskState.afterOfficeStep(step: OfficeStep): SyncDeskState = afterEvents(
    SyncDeskEvent.CabinReported(cabin.applying(SyncDeskEngine.officeChange(step, this))),
    SyncDeskEvent.OfficeStepDone(step),
)

/** One office step put back as the app does it. */
internal fun SyncDeskState.afterOfficeUndo(step: OfficeStep): SyncDeskState = afterEvents(
    SyncDeskEvent.CabinReported(cabin.applying(SyncDeskEngine.officeRestoreChange(step, this))),
    SyncDeskEvent.OfficeStepUndone(step),
)

/**
 * Runs the office sequence the way the runtime does, one due move at a time, until nothing is due
 * (the sequence is on, off, or stopped). [moves] collects the moves in the order they were made.
 */
internal fun SyncDeskState.afterOfficeMoves(moves: MutableList<OfficeMove>? = null, limit: Int = 50): SyncDeskState {
    var state = this
    repeat(limit) {
        val move = state.office.move ?: return state
        moves?.add(move)
        state = if (move.undo) state.afterOfficeUndo(move.step) else state.afterOfficeStep(move.step)
    }
    return state
}

/** Office mode switched fully on, step by step. */
internal fun SyncDeskState.afterOfficeOn(): SyncDeskState = afterEvents(SyncDeskEvent.OfficeRequested).afterOfficeMoves()
