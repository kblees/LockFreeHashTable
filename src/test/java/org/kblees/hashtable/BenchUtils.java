/**
 * Copyright (C) 2023 Karsten Blees
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file or at https://opensource.org/licenses/MIT.
 */
package org.kblees.hashtable;

import java.io.File;
import java.lang.management.*;
import java.lang.reflect.Field;
import java.net.*;
import java.util.*;

import org.kblees.hashtable.primitive.IntHashSet;
import org.openjdk.jmh.profile.*;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.*;

/**
 * Utility methods for benchmarking.
 */
public class BenchUtils
{
	/**
	 * Cannot be instantiated.
	 */
	private BenchUtils()
	{
	}

	/**
	 * Configure benchmark options.
	 *
	 * @param benchmarkClass the benchmark class to execute
	 * @param threads number of threads
	 * @param debug don't fork and add a StackProfiler
	 * @param gcprof add a GCProfiler
	 * @param args command line options
	 * @return the options
	 */
	public static ChainedOptionsBuilder options(Class<?> benchmarkClass, int threads, boolean debug, boolean gcprof,
			String... args)
	{
		ChainedOptionsBuilder options = new OptionsBuilder() //
				.include(benchmarkClass.getName()) //
				.shouldDoGC(true) //
				.resultFormat(ResultFormatType.SCSV) //
				.result(String.format("%s-%d-%tY%<tm%<td-%<tH%<tM%<tS.csv", //
						benchmarkClass.getSimpleName(), threads, new Date()));

		// merge command line options
		final CommandLineOptions clopts;
		try
		{
			clopts = new CommandLineOptions(args);
			if (clopts.shouldHelp())
			{
				clopts.showHelp();
				System.exit(0);
			}
		}
		catch (Exception e)
		{
			throw new RuntimeException(e);
		}

		// override options not specified on the command line
		options.parent(clopts);
		if (!clopts.getThreads().hasValue())
			options.threads(threads);
		if (!clopts.getJvmArgs().hasValue())
			options.jvmArgs("-Xmx28g");

		if (debug)
			options = options.forks(0).addProfiler(StackProfiler.class, "detailLine=true;line=5");
		if (gcprof)
			options = options.addProfiler(GCProfiler.class);
		return options;
	}

	private static void fixMavenClasspath()
	{
		ClassLoader loader = BenchUtils.class.getClassLoader();
		if (loader instanceof URLClassLoader)
		{
			StringBuilder classpath = new StringBuilder();
			for (URL url : ((URLClassLoader) loader).getURLs())
				classpath.append(url.getPath()).append(File.pathSeparator);
			System.setProperty("java.class.path", classpath.toString());
		}
	}

	/**
	 * Runs the benchmark with the specified options.
	 *
	 * @param options the options
	 */
	public static void run(ChainedOptionsBuilder options)
	{
		try
		{
			fixMavenClasspath();
			new Runner(options.build()).run();
		}
		catch (Exception ex)
		{
			throw new RuntimeException(ex);
		}
	}

	/**
	 * Runs the benchmark.
	 *
	 * @param benchmarkClass the benchmark class to execute
	 * @param threads number of threads
	 * @param debug don't fork and add a StackProfiler
	 * @param gcprof add a GCProfiler
	 * @param args command line options
	 */
	public static void run(Class<?> benchmarkClass, int threads, boolean debug, boolean gcprof, String... args)
	{
		run(options(benchmarkClass, threads, debug, gcprof, args));
	}

	/**
	 * Parse a string of the form '[0-9]*[kmg]?'.
	 *
	 * @param value the string to parse
	 * @return parsed value
	 */
	public static long parseScaledLong(String value)
	{
		if (value == null || value.length() == 0)
			return -1;

		long result = 0;
		for (int i = 0; i < value.length(); i++)
		{
			char c = value.charAt(i);
			if (c >= '0' && c <= '9')
			{
				result = 10 * result + (c - '0');
			}
			else
			{
				if (i < value.length() - 1)
					throw new IllegalArgumentException("Illegal char " + c);

				if (c == 'k' || c == 'K')
					result <<= 10;
				else if (c == 'm' || c == 'M')
					result <<= 20;
				else if (c == 'g' || c == 'G')
					result <<= 30;
				else
					throw new IllegalArgumentException("Illegal char " + c);
			}
		}
		return result;
	}

	/**
	 * The golden ratio, for quasi-randomizing integers.
	 */
	private static final int PHI = 0x9e3779b9;

	/**
	 * Quasi-randomizes the value by multiplying with the golden ratio mod 1.
	 *
	 * @param v the value to randomize
	 * @return the randomized value
	 */
	public static int qrng(int v)
	{
		v ^= v >>> 16;
		v ^= v >>> 8;
		return PHI * v;
	}

	private static final Random RND = new Random(0);

	public static int randomInt()
	{
		return RND.nextInt();
	}

	public static int randomInt(int bound)
	{
		long r = Integer.toUnsignedLong(randomInt());
		return (int) ((r * bound) >>> 32);
	}

	public static int[] randomInts(int[] result, int start, int end)
	{
		if (start < 0 || start >= result.length)
			start = 0;
		if (end <= start || end > result.length)
			end = result.length;
		IntHashSet iset = new IntHashSet(end - start);
		for (int i = start; i < end; )
		{
			int rnd = randomInt();
			if (iset.add(rnd))
				result[i++] = rnd;
		}
		return result;
	}

	public static int[] randomInts(int size)
	{
		int[] result = new int[size];
		return randomInts(result, 0, size);
	}

	public static int[] sequentialInts(int size)
	{
		int[] result = new int[size];
		for (int i = 0; i < size; i++)
			result[i] = i;
		return result;
	}

	public static int[] generateInts(int size, boolean random)
	{
		return random ? randomInts(size) : sequentialInts(size);
	}

	public static int[] shuffle(int[] data)
	{
		// random shuffle int array
		for (int i = data.length - 1; i > 1; i--)
		{
			int k = data[i];
			int r = randomInt(i - 1);
			data[i] = data[r];
			data[r] = k;
		}
		return data;
	}

	public static int[] shuffleCopy(int[] data, int from, int to)
	{
		return shuffle(Arrays.copyOfRange(data, from, to));
	}

	public static String[] generateLog2Sizes(int startExp, int endExp, int steps)
	{
		double factor = Math.pow(2, 1d/steps);
		String[] result = new String[(endExp - startExp) * steps];
		int resultIx = 0;
		for (;startExp < endExp; startExp++)
		{
			double v = 1 << startExp;
			for (int i = 0; i < steps; i++, v *= factor)
				result[resultIx++] = String.valueOf(Math.round(v));
		}
		return result;
	}

	public static void printLog2Sizes(int startExp, int endExp, int steps)
	{
		String[] values = generateLog2Sizes(startExp, endExp, steps);
		StringBuilder sb = new StringBuilder();
		for (String value : values)
			sb.append("\"").append(value).append("\", ");
		System.out.println(sb);
	}

	public static Field findField(Class<?> cls, String fieldName)
	{
		for (; cls != null; cls = cls.getSuperclass())
			for (Field field : cls.getDeclaredFields())
				if (fieldName.equals(field.getName()))
					return field;
		return null;
	}

	@SuppressWarnings("unchecked")
	public static <T> T getField(Class<T> type, Object obj, String fieldName)
	{
		return (T) getField(obj, fieldName);
	}

	public static Object getField(Object obj, String fieldName)
	{
		Field field = findField(obj.getClass(), fieldName);
		if (field == null)
			throw new NoSuchFieldError(fieldName);
		field.setAccessible(true);
		try
		{
			return field.get(obj);
		}
		catch (IllegalAccessException e)
		{
			throw new InternalError(e);
		}
	}

	public static long getMaxMemory()
	{
		return Runtime.getRuntime().maxMemory();
	}

	public static long getUsedMemory()
	{
		Runtime rt = Runtime.getRuntime();
		return rt.totalMemory() - rt.freeMemory();
	}

	public static long getGcCount()
	{
		List<GarbageCollectorMXBean> gcbeans = ManagementFactory.getGarbageCollectorMXBeans();
		long totalCount = 0;
		for (GarbageCollectorMXBean bean : gcbeans)
		{
			long count = bean.getCollectionCount();
			if (count > 0)
				totalCount += count;
		}
		return totalCount;
	}

	public static void gc()
	{
		long oldUsedMemory = getUsedMemory();
		long oldGcCount = getGcCount();

		// start gc
		System.gc();

		// wait until garbage collection has completed (max 10s)
		long waitUntil = System.currentTimeMillis() + 10000;
		do
		{
			if (getGcCount() > oldGcCount || getUsedMemory() < oldUsedMemory)
				return;
			try
			{
				Thread.sleep(50);
			}
			catch (InterruptedException ex)
			{
				Thread.currentThread().interrupt();
			}
		} while (System.currentTimeMillis() < waitUntil);
	}
	
	public static void main(String args[])
	{
		printLog2Sizes(14, 19, 6);
	}
}
