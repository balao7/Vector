package com.haroldadmin.vector.state

import com.haroldadmin.vector.VectorState
import kotlinx.coroutines.CoroutineScope

/**
 * Represents actions that can access state or mutate state
 */
internal interface Action<S : VectorState>

/**
 * Represents an action that mutates the current state
 *
 * @param reducer A lambda with the current state as the receiver, which produces a new state
 */
internal inline class SetStateAction<S : VectorState>(val reducer: suspend S.() -> S) :
    Action<S>

/**
 * Represents an action that can access current state and perform some action with it
 *
 * @param block A lambda that receives the current state as its parameter, and performs some action
 * using it
 */
internal inline class GetStateAction<S : VectorState>(val block: suspend (S) -> Unit) :
    Action<S>

/**
 * An entity that manages any [Action] on state.
 *
 * @param S The state type implementing [VectorState]
 *
 * A [SetStateAction] is be processed before any existing [GetStateAction] in the queue
 * A [GetStateAction] is given the latest state value as it's parameter
 */
interface StateProcessor<S : VectorState> : CoroutineScope {

    /**
     * Offer a [SetStateAction] to this processor. This action will be processed as soon as
     * possible, before all existing [GetStateAction] waiting in the queue, if any.
     *
     * @param reducer The action to be offered
     */
    fun offerSetAction(reducer: suspend S.() -> S)

    /**
     * Offer a [GetStateAction] to this processor. The state parameter supplied to this action
     * shall be the latest state value at the time of processing this action.
     *
     * These actions are treated as side effects. When such an action is received, a separate coroutine is launched
     * to process it. This means that when there are multiple such actions waiting in the queue, they will be launched
     * in order, but their completion depends on how long it takes to process them. They will be processed in the
     * coroutine context of their state processor.
     */
    fun offerGetAction(action: suspend (S) -> Unit)

    /**
     * Cleanup any resources held by this processor.
     */
    fun clearProcessor()
}
