using System;
using System.Globalization;
using System.Windows;
using System.Windows.Data;

namespace PasskeyAutofill.App.Converters
{
    /// <summary>
    /// Visible when the bound value's string form equals the ConverterParameter;
    /// otherwise Collapsed. Used to switch UI by the view model's <c>Screen</c>.
    /// Multiple parameters may be supplied comma-separated (any match shows).
    /// </summary>
    public sealed class VisibleWhenConverter : IValueConverter
    {
        public object Convert(object value, Type targetType, object parameter, CultureInfo culture)
        {
            string current = value?.ToString() ?? string.Empty;
            string wanted = parameter?.ToString() ?? string.Empty;
            foreach (var option in wanted.Split(','))
            {
                if (string.Equals(current, option.Trim(), StringComparison.OrdinalIgnoreCase))
                {
                    return Visibility.Visible;
                }
            }

            return Visibility.Collapsed;
        }

        public object ConvertBack(object value, Type targetType, object parameter, CultureInfo culture) =>
            throw new NotSupportedException();
    }

    /// <summary>
    /// Visible when the bound bool is true; Collapsed otherwise. Pass "Invert" as the
    /// ConverterParameter to flip it.
    /// </summary>
    public sealed class BoolToVisibilityConverter : IValueConverter
    {
        public object Convert(object value, Type targetType, object parameter, CultureInfo culture)
        {
            bool flag = value is true;
            if (string.Equals(parameter?.ToString(), "Invert", StringComparison.OrdinalIgnoreCase))
            {
                flag = !flag;
            }

            return flag ? Visibility.Visible : Visibility.Collapsed;
        }

        public object ConvertBack(object value, Type targetType, object parameter, CultureInfo culture) =>
            throw new NotSupportedException();
    }

    /// <summary>Inverse of <see cref="VisibleWhenConverter"/>: Collapsed on match.</summary>
    public sealed class CollapsedWhenConverter : IValueConverter
    {
        public object Convert(object value, Type targetType, object parameter, CultureInfo culture)
        {
            string current = value?.ToString() ?? string.Empty;
            string wanted = parameter?.ToString() ?? string.Empty;
            foreach (var option in wanted.Split(','))
            {
                if (string.Equals(current, option.Trim(), StringComparison.OrdinalIgnoreCase))
                {
                    return Visibility.Collapsed;
                }
            }

            return Visibility.Visible;
        }

        public object ConvertBack(object value, Type targetType, object parameter, CultureInfo culture) =>
            throw new NotSupportedException();
    }
}
