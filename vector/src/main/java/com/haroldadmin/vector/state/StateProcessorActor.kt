package com.haroldadmin.vector.state

import com.haroldadmin.vector.loggers.Logger
import com.haroldadmin.vector.VectorState
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.channels.consumeEach
import java.util.ArrayDeque
import kotlin.coroutines.CoroutineContext

internal class StateProcessorActor<S : VectorState>(
    private val stateHolder: StateHolder<S>,
    private val logger: Logger,
    override val coroutineContext: CoroutineContext
) : StateProcessor<S> {

    private val currentState: S
        get() = stateHolder.state

    private val stateChannel: ConflatedBroadcastChannel<S>
        get() = stateHolder.stateObservable

    private val setStateQueue = ArrayDeque<SetStateAction<S>>()

    private val getStateQueue = ArrayDeque<GetStateAction<S>>()

    val stateProcessingActor = actor<Unit>(
        context = coroutineContext,
        capacity = Channel.UNLIMITED
    ) {

        consumeEach {
            val sentAction = setStateQueue.poll() ?: getStateQueue.poll()
            logger.log("Action: $sentAction")
            when (sentAction) {
                is SetStateAction -> {
                    logger.log("Processing Set-State action")
                    val newState = sentAction.reducer(currentState)
                    logger.log("Setting new state to channel")
                    stateChannel.offer(newState)
                }

                is GetStateAction -> {
                    logger.log("Processing Get-State action")
                    sentAction.block(currentState)
                }

                else -> Unit // sentAction could be null when both the channels have been flushed
            }
        }
    }

    override fun offerSetAction(action: suspend S.() -> S) {
        logger.log("Enqueueing Set-State action")
        setStateQueue.offer(SetStateAction(action))
        logger.log("Flushing queues")
        stateProcessingActor.offer(Unit)
    }

    override fun offerGetAction(action: suspend (S) -> Unit) {
        logger.log("Enqueueing Get-State action")
        getStateQueue.offer(GetStateAction(action))
        logger.log("Flushing queues")
        stateProcessingActor.offer(Unit)
    }

    override fun clearProcessor() {
        logger.log("Clearing State Processor")
        stateProcessingActor.close()
        this.cancel()
    }
}