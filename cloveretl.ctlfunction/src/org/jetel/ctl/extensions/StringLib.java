/*
 * jETeL/CloverETL - Java based ETL application framework.
 * Copyright (c) Javlin, a.s. (info@cloveretl.com)
 *  
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */
package org.jetel.ctl.extensions;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jetel.ctl.Stack;
import org.jetel.ctl.TransformLangExecutorRuntimeException;
import org.jetel.data.DataRecord;
import org.jetel.util.formatter.DateFormatter;
import org.jetel.util.string.StringUtils;

public class StringLib extends TLFunctionLibrary {

	@Override
	public TLFunctionPrototype getExecutable(String functionName) {
		TLFunctionPrototype ret = 
			"concat".equals(functionName) ? new ConcatFunction() :
			"upperCase".equals(functionName) ? new UpperCaseFunction() :
			"lowerCase".equals(functionName) ? new LowerCaseFunction() :
			"substring".equals(functionName) ? new SubstringFunction() :
			"left".equals(functionName) ? new LeftFunction() :
			"right".equals(functionName) ? new RightFunction() :
			"trim".equals(functionName) ? new TrimFunction() : 
			"length".equals(functionName) ? new LengthFunction() :
			"replace".equals(functionName) ? new ReplaceFunction() :
			"split".equals(functionName) ? new SplitFunction() :
			"charAt".equals(functionName) ? new CharAtFunction() :
			"isBlank".equals(functionName) ? new IsBlankFunction() :
			"isAscii".equals(functionName) ? new IsAsciiFunction() :
			"isNumber".equals(functionName) ? new IsNumberFunction() :
			"isInteger".equals(functionName) ? new IsIntegerFunction() :
			"isLong".equals(functionName) ? new IsLongFunction() :
			"isDate".equals(functionName) ? new IsDateFunction() :
			"removeDiacritic".equals(functionName) ? new RemoveDiacriticFunction() :
			"removeBlankSpace".equals(functionName) ? new RemoveBlankSpaceFunction() :
			"removeNonPrintable".equals(functionName) ? new RemoveNonPrintableFunction() :
			"removeNonAscii".equals(functionName) ? new RemoveNonAsciiFunction() :
			"getAlphanumericChars".equals(functionName) ? new GetAlphanumericCharsFunction() :
			"translate".equals(functionName) ? new TranslateFunction() :
			"join".equals(functionName) ? new JoinFunction() :
			"indexOf".equals(functionName) ? new IndexOfFunction() :
			"countChar".equals(functionName) ? new CountCharFunction() :
			"find".equals(functionName) ? new FindFunction() :
			"matches".equals(functionName) ? new MatchesFunction() :
			"chop".equals(functionName) ? new ChopFunction() :
			"cut".equals(functionName) ? new CutFunction() :
			"isUrl".equals(functionName) ? new IsUrlFunction() :
			"getUrlProtocol".equals(functionName) ? new GetUrlProtocolFunction() :
			"getUrlUserInfo".equals(functionName) ? new GetUrlUserInfo() :
			"getUrlHost".equals(functionName) ? new GetUrlHostFunction() :
			"getUrlPort".equals(functionName) ? new GetUrlPortFunction() :
			"getUrlPath".equals(functionName) ? new GetUrlPathFunction() :
			"getUrlQuery".equals(functionName) ? new GetUrlQueryFunction() :
			"getUrlRef".equals(functionName) ? new GetUrlRefFunction() :
			"escapeUrl".equals(functionName) ? new EscapeUrlFunction() :
			"unescapeUrl".equals(functionName) ? new UnescapeUrlFunction() : null;

		if (ret == null) {
    		throw new IllegalArgumentException("Unknown function '" + functionName + "'");
    	}

		return ret;
	}
	
	private static String LIBRARY_NAME = "String";

	public String getName() {
		return LIBRARY_NAME;
	}

	
	// CONCAT
	@TLFunctionAnnotation("Concatenates two or more strings.")
	public static final String concat(TLFunctionCallContext context, String... operands) {
		final StringBuilder buf = new StringBuilder();

		for (int i = 0; i < operands.length; i++) {
			buf.append(operands[i]);
		}

		return buf.toString();
	}

	class ConcatFunction implements TLFunctionPrototype {

		public void init(TLFunctionCallContext context) {
		}
		
		public void execute(Stack stack, TLFunctionCallContext context) {
			String[] args = new String[context.getParams().length];
			for (int i=args.length-1; i>=0; i--) {
				args[i] = stack.popString();
			}
			stack.push(concat(context, args));
		}

	}

	// UPPERCASE
	@TLFunctionAnnotation("Returns input string in uppercase")
	public static final String upperCase(TLFunctionCallContext context, String input) {
		return input.toUpperCase();
	}

	class UpperCaseFunction implements TLFunctionPrototype {

		public void init(TLFunctionCallContext context) {
		}

		public void execute(Stack stack, TLFunctionCallContext context) {
			stack.push(upperCase(context, stack.popString()));
		}
	}

	// LOWERCASE
	@TLFunctionAnnotation("Returns input string in lowercase")
	public static final String lowerCase(TLFunctionCallContext context, String input) {
		return input.toLowerCase();
	}

	class LowerCaseFunction implements TLFunctionPrototype {

		public void init(TLFunctionCallContext context) {
		}

		public void execute(Stack stack, TLFunctionCallContext context) {
			stack.push(lowerCase(context, stack.popString()));
		}

	}

	// SUBSTRING
	@TLFunctionAnnotation("Returns a substring of a given string")
	public static final String substring(TLFunctionCallContext context, String input, int from, int length) {
		return input.substring(from, from+length);
	}

	class SubstringFunction implements TLFunctionPrototype {

		public void init(TLFunctionCallContext context) {
		}

		public void execute(Stack stack, TLFunctionCallContext context) {
			final int length = stack.popInt();
			final int from = stack.popInt();
			final String input = stack.popString();
			stack.push(substring(context, input, from, length));
		}
	}

	// LEFT
	@TLFunctionAnnotation("Returns prefix of the specified length")
	public static final String left(TLFunctionCallContext context, String input, int length) {
		return input.length() < length ? "" : input.substring(0, length);
	}

	@TLFunctionAnnotation("Returns prefix of the specified length. If input string is shorter than specified length " +
		"and 3th argument is true, right side of result is padded with blank spaces so that the result has specified length.")
	public static final String left(TLFunctionCallContext context, String input, int length, boolean spacePad) {
		if (input.length() < length) {
			if (spacePad) {
				return String.format("%-" + length + "s", input);
			}
			return input;
		}
		return left(context, input, length);
	}
	
	class LeftFunction implements TLFunctionPrototype {

		public void init(TLFunctionCallContext context) {
		}

		public void execute(Stack stack, TLFunctionCallContext context) {
			int params = context.getParams().length;
			final boolean spacePad = params > 2 ? stack.popBoolean() : false;
			final int length = stack.popInt();
			final String input = stack.popString();
			if (params > 2) {
				stack.push(left(context, input, length, spacePad));
			} else {
				stack.push(left(context, input, length));
			}
		}
	}

	// RIGHT
	@TLFunctionAnnotation("Returns suffix of the specified length")
	public static final String right(TLFunctionCallContext context, String input, int length) {
		return input.substring(input.length() - length, input.length());
	}

	@TLFunctionAnnotation("Returns suffix of the specified length. If input string is shorter than specified length " +
			"and 3th argument is true, left side of result is padded with blank spaces so that the result has specified length.")
	public static final String right(TLFunctionCallContext context, String input, int length, boolean spacePad) {
		if (input.length() < length) {
			if (spacePad) {
				return String.format("%"+length+"s", input);
			}
			return input;
		}
		return right(context, input, length);
	}

	class RightFunction implements TLFunctionPrototype {

		public void init(TLFunctionCallContext context) {
		}

		public void execute(Stack stack, TLFunctionCallContext context) {
			int params = context.getParams().length;
			final boolean spacePad = params > 2 ? stack.popBoolean() : false;
			final int length = stack.popInt();
			final String input = stack.popString();
			if (params > 2) {
				stack.push(right(context, input, length, spacePad));
			} else {
				stack.push(right(context, input, length));
			}
		}
	}

	// TRIM
	@TLFunctionAnnotation("Removes leading and trailing whitespaces from a string.")
	public static final String trim(TLFunctionCallContext context, String input) {
		StringBuilder buf = new StringBuilder(input);
		return StringUtils.trim(buf).toString();

	}
	class TrimFunction implements TLFunctionPrototype {

		public void init(TLFunctionCallContext context) {
		}

		public void execute(Stack stack, TLFunctionCallContext context) {
			stack.push(trim(context, stack.popString()));
		}
	}

	// LENGTH
	@TLFunctionAnnotation("Returns number of characters in the input string")
	public static final Integer length(TLFunctionCallContext context, String input) {
		return input.length();
	}

	@TLFunctionAnnotation("Returns number of elements in the input list")
	public static final <E> Integer length(TLFunctionCallContext context, List<E> input) {
		return input.size();
	}

	@TLFunctionAnnotation("Returns number of mappings in the input map")
	public static final <K,V> Integer length(TLFunctionCallContext context, Map<K, V> input) {
		return input.size();
	}

	@TLFunctionAnnotation("Returns number of bytes in the input byte array")
	public static Integer length(TLFunctionCallContext context, byte[] input) {
		return input.length;
	}

	@TLFunctionAnnotation("Returns number of fields in the input record")
	public static final Integer length(TLFunctionCallContext context, DataRecord input) {
		return input.getNumFields();
	}

	class LengthFunction implements TLFunctionPrototype {

		public void init(TLFunctionCallContext context) {
		}

		public void execute(Stack stack, TLFunctionCallContext context) {
			if (context.getParams()[0].isString()) {
				stack.push(length(context, stack.popString()));
				return;
			}

			if (context.getParams()[0].isList()) {
				stack.push(length(context, stack.popList()));
				return;
			}

			if (context.getParams()[0].isMap()) {
				stack.push(length(context, stack.popMap()));
				return;
			}

			if (context.getParams()[0].isRecord()) {
				stack.push(length(context, stack.popRecord()));
				return;
			}
			
			if (context.getParams()[0].isByteArray()) {
				stack.push(length(context, stack.popByteArray()));
				return;
			}

			throw new TransformLangExecutorRuntimeException(
					"length - Unknown type: " + context.getParams()[0].name());
		}

	}

	// REPLACE
	@TLFunctionInitAnnotation
	public static final void replaceInit(TLFunctionCallContext context) {
		context.setCache(new TLRegexpCache(context, 1));
	}
	
	@TLFunctionAnnotation("Replaces matches of a regular expression")
	public static final String replace(TLFunctionCallContext context, String input, String regex, String replacement) {
		Matcher m; 
		m = ((TLRegexpCache)context.getCache()).getCachedPattern(context, regex).matcher(input);
		return m.replaceAll(replacement);
	}

	class ReplaceFunction implements TLFunctionPrototype {

		public void init(TLFunctionCallContext context) {
			replaceInit(context);
		}

		public void execute(Stack stack, TLFunctionCallContext context) {
			final String replacement = stack.popString();
			final String regex = stack.popString();
			final String input = stack.popString();
			stack.push(replace(context, input, regex, replacement));
		}

	}

	// SPLIT
	@TLFunctionInitAnnotation
	public static final void splitInit(TLFunctionCallContext context) {
		context.setCache(new TLRegexpCache(context, 1));
	}

	@TLFunctionAnnotation("Splits the string around regular expression matches")
	public static final List<String> split(TLFunctionCallContext context, String input, String regex) {
		final Pattern p = ((TLRegexpCache)context.getCache()).getCachedPattern(context, regex);
		final String[] strArray = p.split(input);
		final List<String> list = new ArrayList<String>();
		for (String item : strArray) {
			list.add(item);
		}
		return list;
	}

	class SplitFunction implements TLFunctionPrototype {

		public void init(TLFunctionCallContext context) {
			splitInit(context);
		}

		public void execute(Stack stack, TLFunctionCallContext context) {
			final String regex = stack.popString();
			final String input = stack.popString();
			stack.push(split(context, input, regex));
		}

	}

	// CHAR AT
	@TLFunctionAnnotation("Returns character at the specified position of input string")
	public static final String charAt(TLFunctionCallContext context, String input, int position) {
		return String.valueOf(input.charAt(position));
	}

	class CharAtFunction implements TLFunctionPrototype {

		public void init(TLFunctionCallContext context) {
		}

		public void execute(Stack stack, TLFunctionCallContext context) {
			int pos = stack.popInt();
			String input = stack.popString();
			stack.push(charAt(context, input, pos));
		}
	}

	// IS BLANK
	@TLFunctionAnnotation("Checks if the string contains only whitespace characters")
	public static final boolean isBlank(TLFunctionCallContext context, String input) {
		return input == null || input.length() == 0
				|| StringUtils.isBlank(input);
	}

	class IsBlankFunction implements TLFunctionPrototype {

		public void init(TLFunctionCallContext context) {
		}

		public void execute(Stack stack, TLFunctionCallContext context) {
			stack.push(isBlank(context, stack.popString()));
		}

	}

	// IS ASCII
	@TLFunctionAnnotation("Checks if the string contains only characters from the US-ASCII encoding")
	public static final boolean isAscii(TLFunctionCallContext context, String input) {
		return StringUtils.isAscii(input);

	}

	class IsAsciiFunction implements TLFunctionPrototype {

		public void init(TLFunctionCallContext context) {
		}

		public void execute(Stack stack, TLFunctionCallContext context) {
			stack.push(isAscii(context, stack.popString()));
		}

	}

	// IS NUMBER
	@TLFunctionAnnotation("Checks if the string can be parsed into a double number")
	public static final boolean isNumber(TLFunctionCallContext context, String input) {
		return StringUtils.isNumber(input);
	}

	class IsNumberFunction implements TLFunctionPrototype {

		public void init(TLFunctionCallContext context) {
		}

		public void execute(Stack stack, TLFunctionCallContext context) {
			stack.push(isNumber(context, stack.popString()));
		}
	}

	// IS INTEGER
	@TLFunctionAnnotation("Checks if the string can be parsed into an integer number")
	public static final boolean isInteger(TLFunctionCallContext context, String input) {
		int result = StringUtils.isInteger(input);
		return result == 0 || result == 1;
	}

	class IsIntegerFunction implements TLFunctionPrototype {

		public void init(TLFunctionCallContext context) {
		}

		public void execute(Stack stack, TLFunctionCallContext context) {
			stack.push(isInteger(context, stack.popString()));
		}
	}

	// IS LONG
	@TLFunctionAnnotation("Checks if the string can be parsed into a long number")
	public static final boolean isLong(TLFunctionCallContext context, String input) {
		int result = StringUtils.isInteger(input);
		return result >= 0 && result < 3;
	}

	class IsLongFunction implements TLFunctionPrototype {

		public void init(TLFunctionCallContext context) {
		}

		public void execute(Stack stack, TLFunctionCallContext context) {
			stack.push(isLong(context, stack.popString()));
		}

	}

	// IS DATE
	@TLFunctionInitAnnotation
    public static final void isDateInit(TLFunctionCallContext context) {
    	context.setCache(new TLDateFormatLocaleCache(context, 1, 2));
    }	
	
	@TLFunctionAnnotation("Checks if the string can be parsed into a date with specified pattern")
	public static final boolean isDate(TLFunctionCallContext context, String input, String pattern) {
		return isDate(context, input, pattern, null);
	}

	@TLFunctionAnnotation("Checks if the string can be parsed into a date with specified pattern and locale.")
	public static final boolean isDate(TLFunctionCallContext context, String input, String pattern, String locale) {
		DateFormatter formatter = ((TLDateFormatLocaleCache)context.getCache()).getCachedLocaleFormatter(context, pattern, locale, 1, 2);
		return formatter.tryParse(input);
	}

	class IsDateFunction implements TLFunctionPrototype {

		public void init(TLFunctionCallContext context) {
			isDateInit(context);
		}

		public void execute(Stack stack, TLFunctionCallContext context) {

			String locale = null;

			if (context.getParams().length > 2) {
				locale = stack.popString();
			}

			final String pattern = stack.popString();
			final String input = stack.popString();

			stack.push(isDate(context, input, pattern, locale));
		}

	}

	// REMOVE DIACRITIC
	@TLFunctionAnnotation("Strips diacritic from characters.")
	public static final String removeDiacritic(TLFunctionCallContext context, String input) {
		return StringUtils.removeDiacritic(input);
	}

	class RemoveDiacriticFunction implements TLFunctionPrototype {

		public void init(TLFunctionCallContext context) {
		}

		public void execute(Stack stack, TLFunctionCallContext context) {
			stack.push(removeDiacritic(context, stack.popString()));
		}
	}

	// REMOVE BLANK SPACE
	@TLFunctionAnnotation("Removes whitespace characters")
	public static final String removeBlankSpace(TLFunctionCallContext context, String input) {
		return StringUtils.removeBlankSpace(input);
	}

	class RemoveBlankSpaceFunction implements TLFunctionPrototype {

		public void init(TLFunctionCallContext context) {
		}

		public void execute(Stack stack, TLFunctionCallContext context) {
			stack.push(removeBlankSpace(context, stack.popString()));
		}
	}

	// REMOVE NONPRINTABLE CHARS
	@TLFunctionAnnotation("Removes nonprintable characters")
	public static final String removeNonPrintable(TLFunctionCallContext context, String input) {
		return StringUtils.removeNonPrintable(input);
	}

	class RemoveNonPrintableFunction implements TLFunctionPrototype {

		public void init(TLFunctionCallContext context) {
		}

		public void execute(Stack stack, TLFunctionCallContext context) {
			stack.push(removeNonPrintable(context, stack.popString()));
		}

	}

	// REMOVE NONASCII CHARS
	@TLFunctionAnnotation("Removes nonascii characters")
	public static final String removeNonAscii(TLFunctionCallContext context, String input) {
		return StringUtils.removeNonAscii(input);
	}

	class RemoveNonAsciiFunction implements TLFunctionPrototype {

		public void init(TLFunctionCallContext context) {
		}

		public void execute(Stack stack, TLFunctionCallContext context) {
			stack.push(removeNonAscii(context, stack.popString()));
		}

	}

	// GET ALPHANUMERIC CHARS
	@TLFunctionAnnotation("Extracts only alphanumeric characters from input string")
	public static final String getAlphanumericChars(TLFunctionCallContext context, String input) {
		return getAlphanumericChars(context, input, true, true);
	}

	@TLFunctionAnnotation("Extracts letters, numbers or both from input string")
	public static final String getAlphanumericChars(TLFunctionCallContext context, String input,
			boolean takeAlpha, boolean takeNumeric) {
		return StringUtils.getOnlyAlphaNumericChars(input, takeAlpha,
				takeNumeric);
	}

	class GetAlphanumericCharsFunction implements TLFunctionPrototype {

		public void init(TLFunctionCallContext context) {
		}

		public void execute(Stack stack, TLFunctionCallContext context) {
			if (context.getParams().length == 1) {
				stack.push(getAlphanumericChars(context, stack.popString()));
			} else {
				final boolean takeNumeric = stack.popBoolean();
				final boolean takeAlpha = stack.popBoolean();
				final String input = stack.popString();
				stack.push(getAlphanumericChars(context, input, takeAlpha,
								takeNumeric));
			}
		}

	}

	// TRANSLATE
	@TLFunctionAnnotation("Replaces occurences of characters")
	public static final String translate(TLFunctionCallContext context, String input, String match,
			String replacement) {
		return String.valueOf(StringUtils.translateSequentialSearch(input,
				match, replacement));
	}

	class TranslateFunction implements TLFunctionPrototype {

		public void init(TLFunctionCallContext context) {
		}

		public void execute(Stack stack, TLFunctionCallContext context) {
			final String replacement = stack.popString();
			final String match = stack.popString();
			final String input = stack.popString();

			stack.push(translate(context, input, match, replacement));
		}

	}

	// JOIN
	@TLFunctionAnnotation("Concatenets list elements into a string using delimiter.")
	public static final <E> String join(TLFunctionCallContext context, String delimiter, List<E> values) {
		StringBuffer buf = new StringBuffer();
		for (int i=0; i<values.size(); i++) {
			buf.append(values.get(i) == null ? "null" : values.get(i).toString());
			if (i < values.size()-1) {
				buf.append(delimiter);
			}
		}

		return buf.toString();
	}

	@TLFunctionAnnotation("Concatenates all mappings into a string using delimiter.")
	public static final <K,V> String join(TLFunctionCallContext context, String delimiter, Map<K,V> values) {
		StringBuffer buf = new StringBuffer();
		Set<K> keys = values.keySet();
		for (Iterator<K> it = keys.iterator(); it.hasNext();) {
			K key = it.next();
			V value = values.get(key);
			buf.append(key.toString()).append("=").append(
					value == null ? "null" : value.toString());
			if (it.hasNext()) {
				buf.append(delimiter);
			}
			
		}

		return buf.toString();
	}

	class JoinFunction implements TLFunctionPrototype {

		public void init(TLFunctionCallContext context) {
		}

		public void execute(Stack stack, TLFunctionCallContext context) {
			if (context.getParams()[1].isList()) {
				final List<Object> values = stack.popList();
				final String delim = stack.popString();
				stack.push(join(context, delim, values));
			} else {
				final Map<Object, Object> values = stack.popMap();
				final String delim = stack.popString();
				stack.push(join(context, delim, values));
			}
		}

	}

	// INDEX OF
	@TLFunctionAnnotation("Returns the first occurence of a specified string")
	public static final Integer indexOf(TLFunctionCallContext context, String input, String pattern) {
		return indexOf(context, input, pattern, 0);
	}

	@TLFunctionAnnotation("Returns the first occurence of a specified string")
	public static final Integer indexOf(TLFunctionCallContext context, String input, String pattern, int from) {
		return StringUtils.indexOf(input, pattern, from);
	}

	class IndexOfFunction implements TLFunctionPrototype {

		public void init(TLFunctionCallContext context) {
		}

		public void execute(Stack stack, TLFunctionCallContext context) {
			int from = 0;

			if (context.getParams().length > 2) {
				from = stack.popInt();
			}

			final String pattern = stack.popString();
			final String input = stack.popString();
			stack.push(indexOf(context, input, pattern, from));
		}

	}

	// COUNTCHAR
	@TLFunctionAnnotation("Calculates the number of occurences of the specified character")
	public static final Integer countChar(TLFunctionCallContext context, String input, String character) {
		return StringUtils.count(input, character.charAt(0));
	}

	class CountCharFunction implements TLFunctionPrototype {

		public void init(TLFunctionCallContext context) {
		}

		public void execute(Stack stack, TLFunctionCallContext context) {
			String character = stack.popString();
			String input = stack.popString();
			stack.push(countChar(context, input, character));
		}
	}

	// FIND
	@TLFunctionInitAnnotation
	public static final void findInit(TLFunctionCallContext context) {
		context.setCache(new TLRegexpCache(context, 1));
	}
	
	@TLFunctionAnnotation("Finds and returns all occurences of regex in specified string")
	public static final List<String> find(TLFunctionCallContext context, String input, String pattern) {
		
		Matcher m = ((TLRegexpCache)context.getCache()).getCachedPattern(context, pattern).matcher(input);
		
		final List<String> ret = new ArrayList<String>();

		while (m.find()) {
			ret.add(m.group());
		}

		return ret;
	}

	class FindFunction implements TLFunctionPrototype {

		public void init(TLFunctionCallContext context) {
			findInit(context);
		}

		public void execute(Stack stack, TLFunctionCallContext context) {
			final String pattern = stack.popString();
			final String input = stack.popString();
			stack.push(find(context, input, pattern));
			return;
		}
	}
	
	// MATCHES
	@TLFunctionInitAnnotation
	public static final void matchesInit(TLFunctionCallContext context) {
		context.setCache(new TLRegexpCache(context, 1));
	}
	
	@TLFunctionAnnotation("Tries to match entire input with specified pattern.")
	public static final Boolean matches(TLFunctionCallContext context, String input, String pattern) {
		
		Matcher m = ((TLRegexpCache)context.getCache()).getCachedPattern(context, pattern).matcher(input);
		return m.matches();
	}

	class MatchesFunction implements TLFunctionPrototype {

		public void init(TLFunctionCallContext context) {
			matchesInit(context);
		}

		public void execute(Stack stack, TLFunctionCallContext context) {
			final String pattern = stack.popString();
			final String input = stack.popString();
			stack.push(matches(context, input, pattern));
			return;
		}
	}
	
	// CHOP
	@TLFunctionInitAnnotation()
	public static final void chopInit(TLFunctionCallContext context) {
		context.setCache(new TLRegexpCache(context, 1));
	}
	
	private static final Pattern chopPattern = Pattern.compile("[\r\n]+");
	
	@TLFunctionAnnotation("Removes new line characters from input string")
	public static final String chop(TLFunctionCallContext context, String input) {
		final Matcher m = chopPattern.matcher(input);
		return m.replaceAll("");
	}

	@TLFunctionAnnotation("Removes pattern from input string")
	public static final String chop(TLFunctionCallContext context, String input, String pattern) {
		final Pattern p = ((TLRegexpCache)context.getCache()).getCachedPattern(context, pattern);
		final Matcher m = p.matcher(input);
		return m.replaceAll("");

	}
	class ChopFunction implements TLFunctionPrototype {

		public void init(TLFunctionCallContext context) {
			chopInit(context);
		}

		public void execute(Stack stack, TLFunctionCallContext context) {
			if (context.getParams().length == 1) {
				stack.push(chop(context, stack.popString()));
			} else {
				final String pattern = stack.popString();
				final String input = stack.popString();
				stack.push(chop(context, input, pattern));

			}
		}

	}

	//CUT
	@TLFunctionAnnotation("Cuts substring from specified string based on list consisting of pairs position,length")
	public static final List<String> cut(TLFunctionCallContext context, String input, List<Integer> indexes) {
		if (indexes.size() % 2 != 0) {
			throw new TransformLangExecutorRuntimeException(
					"Incorrect number of indices: " + indexes.size()
							+ ". Must be an even number");
		}
		final Iterator<Integer> it = indexes.iterator();
		final List<String> ret = new ArrayList<String>();

		while (it.hasNext()) {
			final Integer from = it.next();
			final Integer length = it.next();
			ret.add(input.substring(from, from + length));
		}

		return ret;
	}

	class CutFunction implements TLFunctionPrototype {

		public void init(TLFunctionCallContext context) {
		}

		public void execute(Stack stack, TLFunctionCallContext context) {
			final List<Object> indices = stack.popList();
			final String input = stack.popString();
			stack.push(cut(context, input, TLFunctionLibrary.<Integer>convertTo(indices)));
		}
	}

	// URL parsing
	
    //   foo://username:password@example.com:8042/over/there/index.dtb?type=animal;name=narwhal#nose
    //   \_/   \_______________/ \_________/ \__/\___________________/ \______________________/ \__/
    //    |           |               |       |            |                    |                |
    //  scheme    userinfo         hostname  port        path                 query             ref

		   
	@TLFunctionAnnotation("Checks whether specified string is valid URL")
	public static final boolean isUrl(TLFunctionCallContext context, String url) {
		try {
			new URL(url);
			return true;
		} catch (MalformedURLException e) {
			return false;
		}
	}
	
	class IsUrlFunction implements TLFunctionPrototype {
	
		public void init(TLFunctionCallContext context) {
		}
		
		public void execute(Stack stack, TLFunctionCallContext context) {
			final String url = stack.popString();
			stack.push(isUrl(context, url));
		}
	}

	@TLFunctionAnnotation("Parses out protocol name from specified URL")
	public static final String getUrlProtocol(TLFunctionCallContext context, String url) {
		try {
			return new URL(url).getProtocol();
		} catch (MalformedURLException e) {
			return null;
		}
	}
	
	class GetUrlProtocolFunction implements TLFunctionPrototype {
	
		public void init(TLFunctionCallContext context) {
		}
		
		public void execute(Stack stack, TLFunctionCallContext context) {
			final String url = stack.popString();
			stack.push(getUrlProtocol(context, url));
		}
	}
	
	@TLFunctionAnnotation("Parses out user info from specified URL")
	public static final String getUrlUserInfo(TLFunctionCallContext context, String url) {
		try {
			String ui = new URL(url).getUserInfo();
			return ui == null ? "" : ui;
		} catch (MalformedURLException e) {
			return null;
		}
	}
	
	class GetUrlUserInfo implements TLFunctionPrototype {
	
		public void init(TLFunctionCallContext context) {
		}
		
		public void execute(Stack stack, TLFunctionCallContext context) {
			final String url = stack.popString();
			stack.push(getUrlUserInfo(context, url));
		}
	}

	@TLFunctionAnnotation("Parses out host name from specified URL")
	public static final String getUrlHost(TLFunctionCallContext context, String url) {
		try {
			return new URL(url).getHost();
		} catch (MalformedURLException e) {
			return null;
		}
	}
	
	class GetUrlHostFunction implements TLFunctionPrototype {
	
		public void init(TLFunctionCallContext context) {
		}
		
		public void execute(Stack stack, TLFunctionCallContext context) {
			final String url = stack.popString();
			stack.push(getUrlHost(context, url));
		}
	}
	
	@TLFunctionAnnotation("Parses out port number from specified URL. Returns -1 if port not defined, -2 if URL has invalid syntax.")
	public static final int getUrlPort(TLFunctionCallContext context, String url) {
		try {
			return new URL(url).getPort();
		} catch (MalformedURLException e) {
			return -2;
		}
	}
	
	class GetUrlPortFunction implements TLFunctionPrototype {
	
		public void init(TLFunctionCallContext context) {
		}
		
		public void execute(Stack stack, TLFunctionCallContext context) {
			final String url = stack.popString();
			stack.push(getUrlPort(context, url));
		}
	}

	@TLFunctionAnnotation("Parses out path part of specified URL")
	public static final String getUrlPath(TLFunctionCallContext context, String url) {
		try {
			return new URL(url).getPath();
		} catch (MalformedURLException e) {
			return null;
		}
	}
	
	class GetUrlPathFunction implements TLFunctionPrototype {
	
		public void init(TLFunctionCallContext context) {
		}
		
		public void execute(Stack stack, TLFunctionCallContext context) {
			final String url = stack.popString();
			stack.push(getUrlPath(context, url));
		}
	}

	@TLFunctionAnnotation("Parses out query (parameters) from specified URL")
	public static final String getUrlQuery(TLFunctionCallContext context, String url) {
		try {
			String q = new URL(url).getQuery();
			return q == null ? "" : q;
		} catch (MalformedURLException e) {
			return null;
		}
	}
	
	class GetUrlQueryFunction implements TLFunctionPrototype {
	
		public void init(TLFunctionCallContext context) {
		}
		
		public void execute(Stack stack, TLFunctionCallContext context) {
			final String url = stack.popString();
			stack.push(getUrlQuery(context, url));
		}
	}
	
	@TLFunctionAnnotation("Parses out fragment after \"#\" character, also known as ref, reference or anchor, from specified URL")
	public static final String getUrlRef(TLFunctionCallContext context, String url) {
		try {
			String query = new URL(url).getRef();
			return query == null ? "" : query;
		} catch (MalformedURLException e) {
			return null;
		}
	}
	
	class GetUrlRefFunction implements TLFunctionPrototype {
	
		public void init(TLFunctionCallContext context) {
		}
		
		public void execute(Stack stack, TLFunctionCallContext context) {
			final String url = stack.popString();
			stack.push(getUrlRef(context, url));
		}
	}
	

	// ESCAPE URL
	
	@TLFunctionAnnotation("Escapes any illegal characters within components of specified URL (the URL is parsed first).")
	public static final String escapeUrl(TLFunctionCallContext context, String urlStr) {
		try {
			// parse input string
			URL url = new URL(urlStr);
			// create URI representation of the URL which handles character quoting
			return new URI(url.getProtocol(), url.getUserInfo(), url.getHost(), url.getPort(), url.getPath(), url.getQuery(), url.getRef()).toASCIIString();
		} catch (MalformedURLException e) {
			throw new TransformLangExecutorRuntimeException("Failed to parse input string as URL", e);
		} catch (URISyntaxException e) {
			throw new TransformLangExecutorRuntimeException("Failed to escape URL", e);
		}
	}
	
	class EscapeUrlFunction implements TLFunctionPrototype {
	
		public void init(TLFunctionCallContext context) {
		}
		
		public void execute(Stack stack, TLFunctionCallContext context) {
			final String url = stack.popString();
			stack.push(escapeUrl(context, url));
		}
	}

	
	// UNESCAPE URL
	
	@TLFunctionAnnotation("Decodes sequence of escaped octets with sequence of characters that it represents in UTF-8, e.g. \"%20\" is replaced with \" \".")
	public static final String unescapeUrl(TLFunctionCallContext context, String url) {
		try {
			// try to parse passed string as URL and convert it to URI which handles escaping of characters
			URI uri = new URL(url).toURI();
			
			// get unescaped parts of the URL
			String scheme = uri.getScheme();
			String authority = uri.getAuthority();
			String path = uri.getPath();
			String query = uri.getQuery();
			String fragment = uri.getFragment();
			
			// reconstruct URL from unescaped parts and return its String representation
			StringBuilder sb = new StringBuilder();
			if (scheme != null) sb.append(scheme).append("://");
			if (authority != null) sb.append(authority);
			if (path != null) sb.append(path);
			if (query != null) sb.append('?').append(query);
			if (fragment != null) sb.append('#').append(fragment);
			
			return new URL(sb.toString()).toString();
		} catch (MalformedURLException e) {
			throw new TransformLangExecutorRuntimeException("Failed to unescape URL", e);
		} catch (URISyntaxException e) {
			throw new TransformLangExecutorRuntimeException("Failed to unescape URL. Wrong syntax of input string?", e);
		}
	}
	
	class UnescapeUrlFunction implements TLFunctionPrototype {
	
		public void init(TLFunctionCallContext context) {
		}
		
		public void execute(Stack stack, TLFunctionCallContext context) {
			final String url = stack.popString();
			stack.push(unescapeUrl(context, url));
		}
	}
	
}
