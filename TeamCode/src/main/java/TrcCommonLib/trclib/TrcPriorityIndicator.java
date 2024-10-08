
package TrcCommonLib.trclib;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;

/**
 * This class implements a priority indicator device that supports priority list. A priority list specifies a list of
 * indicator patterns in priority order. This means that if the indicator is set to a given pattern, it will be updated
 * only if the pattern being set has a higher priority than the pattern that is already active. This allows the
 * indicator to be used to display important status that will not be overwritten by unimportant status change. This
 * class is intended to be extended by a device dependent subclass that provides device dependent methods to set and
 * get indicator patterns.
 *
 * @param <T> specifies the device dependent indicator pattern type.
 */
public abstract class TrcPriorityIndicator<T>
{
    /**
     * This method gets the current set pattern.
     *
     * @return currently set pattern.
     */
    public abstract T getPattern();

    /**
     * This method sets the pattern to the physical indicator device in a device dependent way.
     *
     * @param pattern specifies the indicator pattern. If null, turn off the indicator pattern.
     */
    public abstract void setPattern(T pattern);

    /**
     * This class implements the pattern state. It contains the pattern and the state if the pattern is active or not.
     */
    private class PatternState
    {
        final T pattern;
        boolean enabled;
        double onDuration;
        double offDuration;
        boolean on;
        double expiredTime;

        /**
         * Constructor: Create an instance of the object.
         *
         * @param pattern specifies the pattern.
         * @param enabled specifies the initial state of the pattern.
         * @param onDuration specifies the time in seconds the pattern remains ON, zero to turn it ON indefinitely.
         * @param offDuration specifies the time in seconds the pattern remains OFF, then turn the LED back ON.
         *        Zero for disabling after onDuration expires.
         */
        public PatternState(T pattern, boolean enabled, double onDuration, double offDuration)
        {
            this.pattern = pattern;
            this.enabled = enabled;
            this.onDuration = onDuration;
            this.offDuration = offDuration;
            this.on = false;
            this.expiredTime = 0.0;
        }   //PatternState

        /**
         * Constructor: Create an instance of the object.
         *
         * @param pattern specifies the indicator pattern.
         */
        public PatternState(T pattern)
        {
            this(pattern, false, 0.0, 0.0);
        }   //PatternState

        /**
         * This method returns the string representation of the LED pattern state.
         *
         * @return string representation of the LED pattern state.
         */
        @Override
        public String toString()
        {
            return String.format(
                Locale.US, "%s: enabled=%s, on=%s, expiredTime=%.3f", pattern, enabled, on, expiredTime);
        }   //toString

    }   //class PatternState

    private final HashMap<String, T> namedPatternMap = new HashMap<>();

    protected final TrcDbgTrace tracer;
    protected final String instanceName;
    private final TrcTaskMgr.TaskObject indicatorTaskObj;
    private PatternState[] patternPriorities = null;
    private boolean taskEnabled = false;
    private double nextTaskRunTime;

    /**
     * Constructor: Create an instance of the object.
     *
     * @param instanceName specifies the instance name.
     */
    public TrcPriorityIndicator(String instanceName)
    {
        this.tracer = new TrcDbgTrace(instanceName);
        this.instanceName = instanceName;
        indicatorTaskObj = TrcTaskMgr.createTask(instanceName, this::indicatorTask);
    }   //TrcPriorityIndicator

    /**
     * This method returns the instance name.
     *
     * @return instance name.
     */
    @Override
    public String toString()
    {
        return instanceName;
    }   //toString

    /**
     * This method enables/disables the Indicator Task that periodically updates the indicator states.
     *
     * @param enabled specifies true to enable task, false to disable.
     */
    private void setTaskEnabled(boolean enabled)
    {
        if (enabled && !taskEnabled)
        {
            // Enabling task.
            indicatorTaskObj.registerTask(TrcTaskMgr.TaskType.OUTPUT_TASK);
            nextTaskRunTime = TrcTimer.getCurrentTime();
        }
        else if (!enabled && taskEnabled)
        {
            // Disabling task.
            indicatorTaskObj.unregisterTask();
        }

        taskEnabled = enabled;
    }   //setTaskEnabled

    /**
     * This method prints the Pattern Priority table to the given trace output for debugging purpose.
     *
     * @param msgTracer specifies the tracer object to use to print the info.
     */
    public synchronized void printPatternPriorityTable(TrcDbgTrace msgTracer)
    {
        if (patternPriorities != null)
        {
            StringBuilder msg = new StringBuilder("PatternPriorities=");

            for (PatternState state: patternPriorities)
            {
                msg.append("\n\t");
                msg.append(state);
            }

            if (msgTracer == null)
            {
                msgTracer = this.tracer;
            }

            msgTracer.traceInfo(instanceName, msg.toString());
        }
    }   //printPatternPriorityTable

    /**
     * This method turns the indicator off.
     */
    public void reset()
    {
        setPattern(null);
    }   //reset

    /**
     * This method enables/disables the pattern in the priority list.
     *
     * @param pattern specifies the pattern in the priority list.
     * @param enabled specifies true to turn the pattern ON, false to turn it OFF.
     * @param onDuration specifies the time in seconds the pattern remains ON, zero to turn it ON indefinitely.
     * @param offDuration specifies the time in seconds the pattern remains OFF, then turn the LED back ON.
     *        Zero for disabling after onDuration expires.
     */
    public synchronized void setPatternState(T pattern, boolean enabled, double onDuration, double offDuration)
    {
        int index = getPatternPriority(pattern);

        tracer.traceDebug(
            instanceName, "[%d] pattern=%s, enabled=%s, onDuration=%.3f, offDuration=%.3f",
            index, pattern, enabled, onDuration, offDuration);
        if (index != -1)
        {
            patternPriorities[index].enabled = enabled;
            if (enabled)
            {
                patternPriorities[index].on = true;
                patternPriorities[index].onDuration = onDuration;
                patternPriorities[index].offDuration = onDuration > 0.0? offDuration: 0.0;
                patternPriorities[index].expiredTime = onDuration > 0.0? TrcTimer.getCurrentTime() + onDuration: 0.0;
            }
            else
            {
                patternPriorities[index].on = false;
                patternPriorities[index].onDuration = 0.0;
                patternPriorities[index].offDuration = 0.0;
                patternPriorities[index].expiredTime = 0.0;
            }
        }
    }   //setPatternState

    /**
     * This method enables/disables the pattern in the priority list.
     *
     * @param pattern specifies the pattern in the priority list.
     * @param enabled specifies true to turn the pattern ON, false to turn it OFF.
     * @param onDuration specifies the time in seconds the pattern remains ON, zero to turn it ON indefinitely.
     */
    public void setPatternState(T pattern, boolean enabled, double onDuration)
    {
        setPatternState(pattern, enabled, onDuration, 0.0);
    }   //setPatternState

    /**
     * This method enables/disables the pattern in the priority list.
     *
     * @param pattern specifies the pattern in the priority list.
     * @param enabled specifies true to turn the pattern ON, false to turn it OFF.
     */
    public void setPatternState(T pattern, boolean enabled)
    {
        setPatternState(pattern, enabled, 0.0, 0.0);
    }   //setPatternState

    /**
     * This method enables/disables the pattern in the priority list.
     *
     * @param patternName specifies the name of the pattern in the priority list.
     * @param enabled specifies true to turn the pattern ON, false to turn it OFF.
     * @param onDuration specifies the time in seconds the pattern remains ON, zero to turn it ON indefinitely.
     * @param offDuration specifies the time in seconds the pattern remains OFF, then turn the LED back ON.
     *        Zero for disabling after onDuration expires.
     * @throws IllegalAccessError when patternName is not found in the map.
     */
    public void setPatternState(String patternName, boolean enabled, double onDuration, double offDuration)
    {
        setPatternState(namedPatternMap.get(patternName), enabled, onDuration, offDuration);
    }   //setPatternState

    /**
     * This method enables/disables the pattern in the priority list.
     *
     * @param patternName specifies the name of the pattern in the priority list.
     * @param enabled specifies true to turn the pattern ON, false to turn it OFF.
     * @param onDuration specifies the time in seconds the pattern remains ON, zero to turn it ON indefinitely.
     * @throws IllegalAccessError when patternName is not found in the map.
     */
    public void setPatternState(String patternName, boolean enabled, double onDuration)
    {
        setPatternState(namedPatternMap.get(patternName), enabled, onDuration, 0.0);
    }   //setPatternState

    /**
     * This method enables/disables the pattern in the priority list.
     *
     * @param patternName specifies the name of the pattern in the priority list.
     * @param enabled specifies true to turn the pattern ON, false to turn it OFF.
     * @throws IllegalAccessError when patternName is not found in the map.
     */
    public void setPatternState(String patternName, boolean enabled)
    {
        setPatternState(namedPatternMap.get(patternName), enabled, 0.0, 0.0);
    }   //setPatternState

    /**
     * This method returns the pattern state if it is in the priority list. If the pattern is not in the list,
     * it returns false.
     *
     * @param pattern specifies the pattern in the priority list.
     * @return true if the pattern is ON, false if it is OFF.
     */
    public synchronized boolean getPatternState(T pattern)
    {
        boolean state = false;
        int index = getPatternPriority(pattern);

        if (index != -1)
        {
            state = patternPriorities[index].enabled;
        }
        tracer.traceDebug(instanceName, "pattern=" + pattern + ",index=" + index + ",state=" + state);

        return state;
    }   //getPatternState

    /**
     * This method returns the pattern state if it is in the priority list. If the pattern is not in the list,
     * it returns false.
     *
     * @param patternName specifies the name of the pattern in the priority list.
     * @return true if the pattern is ON, false if it is OFF.
     * @throws IllegalAccessError when patternName is not found in the map.
     */
    public boolean getPatternState(String patternName)
    {
        return getPatternState(namedPatternMap.get(patternName));
    }   //getPatternState

    /**
     * This method resets all pattern states in the pattern priority list and set the indicator device to non-active
     * state.
     */
    public synchronized void resetAllPatternStates()
    {
        if (patternPriorities != null)
        {
            for (PatternState state : patternPriorities)
            {
                state.enabled = false;
                state.on = false;
                state.expiredTime = 0.0;
            }

            reset();
        }
    }   //resetAllPatternStates

    /**
     * This method searches the given pattern priorities array for the given pattern. If found, its index is
     * the priority and will be returned. If the pattern is not found in the array, -1 will be return which also
     * means the lowest priority.
     *
     * @param pattern specifies the indicator pattern to be searched in the pattern priorities array.
     * @return the pattern priority if found, -1 if not found.
     */
    public synchronized int getPatternPriority(T pattern)
    {
        int priority = -1;

        if (patternPriorities != null)
        {
            for (int i = 0; i < patternPriorities.length; i++)
            {
                if (pattern == patternPriorities[i].pattern)
                {
                    priority = i;
                    break;
                }
            }
        }
        tracer.traceDebug(instanceName, "pattern=" + pattern + ",priority=" + priority);

        return priority;
    }   //getPatternPriority

    /**
     * This method sets the pattern priority list for operations that need it. The priority list must be sorted in
     * decreasing priorities.
     *
     * @param priorities specifies the pattern priority list or null to disregard the previously set list.
     */
    public synchronized void setPatternPriorities(T[] priorities)
    {
        tracer.traceDebug(
            instanceName, "priorityList=" + (priorities == null ? "null" : Arrays.toString(priorities)));
        if (priorities != null)
        {
            PatternState[] oldPriorities = patternPriorities;
            patternPriorities = (PatternState[]) Array.newInstance(PatternState.class, priorities.length);

            namedPatternMap.clear();
            for (int i = 0; i < patternPriorities.length; i++)
            {
                patternPriorities[i] = new PatternState(priorities[i]);
                namedPatternMap.put(patternPriorities[i].pattern.toString(), patternPriorities[i].pattern);
            }

            // If we had a previous priority list, make sure patterns persist
            if (oldPriorities != null)
            {
                for (PatternState patternState : oldPriorities)
                {
                    if (patternState.enabled)
                    {
                        // This will silently fail if this pattern is not in the priority list
                        setPatternState(patternState.pattern, true, patternState.onDuration, patternState.offDuration);
                    }
                }
            }
            updateIndicator();
            setTaskEnabled(true);
        }
        else
        {
            setTaskEnabled(false);
            patternPriorities = null;
            namedPatternMap.clear();
            reset();
        }
    }   //setPatternPriorities

    /**
     * This method is called to update the pattern according to the patternPriorities list. It will turn on the
     * highest priority pattern if enabled. If none of the patterns in the priority list is enabled, it will set
     * the indicator device to non-active state.
     */
    private synchronized void updateIndicator()
    {
        double currTime = TrcTimer.getCurrentTime();
        boolean gotPattern = false;
        T pattern = null;

        for (PatternState patternState: patternPriorities)
        {
            // Going from highest priority and down to low.
            if (patternState.enabled)
            {
                if (patternState.expiredTime > 0.0 && currTime >= patternState.expiredTime)
                {
                    patternState.expiredTime = 0.0;
                    if (patternState.on)
                    {
                        // ON has expired.
                        tracer.traceDebug(instanceName, "Pattern " + patternState.pattern + " ON has expired.");
                        patternState.on = false;
                        if (patternState.offDuration > 0.0)
                        {
                            patternState.expiredTime = currTime + patternState.offDuration;
                        }
                        else
                        {
                            patternState.enabled = false;
                        }
                    }
                    else if (patternState.onDuration > 0.0)
                    {
                        // OFF has expired.
                        tracer.traceDebug(instanceName, "Pattern " + patternState.pattern + " OFF has expired.");
                        patternState.on = true;
                        patternState.expiredTime = currTime + patternState.onDuration;
                    }
                }

                if (!gotPattern && patternState.enabled)
                {
                    // Highest priority pattern that's enabled and not expired.
                    gotPattern = true;
                    pattern = patternState.on? patternState.pattern: null;
                }
            }
        }
        //
        // Only set the pattern if it is not already active.
        //
        if (pattern != getPattern())
        {
            setPattern(pattern);
        }
        tracer.traceDebug(instanceName, "pattern=" + pattern);
    }   //updateIndicator

    /**
     * This task when enabled runs every 100 msec to update the indicator states.
     *
     * @param taskType specifies the task type (not used).
     * @param runMode specifies the robot run mode (not used).
     * @param slowPeriodicLoop specifies true if it is running the slow periodic loop on the main robot thread,
     *        false otherwise.
     */
    private void indicatorTask(TrcTaskMgr.TaskType taskType, TrcRobot.RunMode runMode, boolean slowPeriodicLoop)
    {
        double currTime = TrcTimer.getCurrentTime();

        if (currTime >= nextTaskRunTime)
        {
            // Runs every 100 msec.
            nextTaskRunTime = currTime + 0.05;
            updateIndicator();
        }
    }   //indicatorTask

}   //class TrcPriorityIndicator
