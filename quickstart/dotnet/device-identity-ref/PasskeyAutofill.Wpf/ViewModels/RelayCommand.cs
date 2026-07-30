using System;
using System.Windows.Input;

namespace PasskeyAutofill.App.ViewModels
{
    /// <summary>Minimal ICommand for binding buttons to view-model methods.</summary>
    public sealed class RelayCommand : ICommand
    {
        private readonly Action _execute;
        private readonly Func<bool>? _canExecute;

        public RelayCommand(Action execute, Func<bool>? canExecute = null)
        {
            _execute = execute;
            _canExecute = canExecute;
        }

        public bool CanExecute(object? parameter) => _canExecute?.Invoke() ?? true;

        public void Execute(object? parameter) => _execute();

        public event EventHandler? CanExecuteChanged;

        public void RaiseCanExecuteChanged() =>
            CanExecuteChanged?.Invoke(this, EventArgs.Empty);
    }

    /// <summary>ICommand that passes a typed parameter to its handler.</summary>
    public sealed class RelayCommand<T> : ICommand
    {
        private readonly Action<T> _execute;
        private readonly Func<T, bool>? _canExecute;

        public RelayCommand(Action<T> execute, Func<T, bool>? canExecute = null)
        {
            _execute = execute;
            _canExecute = canExecute;
        }

        // WPF can call these with null (e.g. the CommandManager's initial probe) or,
        // in principle, a differently-typed parameter. Guard instead of casting blindly.
        public bool CanExecute(object? parameter) =>
            _canExecute is null || (parameter is T value && _canExecute(value));

        public void Execute(object? parameter)
        {
            if (parameter is T value)
            {
                _execute(value);
            }
        }

        public event EventHandler? CanExecuteChanged;

        public void RaiseCanExecuteChanged() =>
            CanExecuteChanged?.Invoke(this, EventArgs.Empty);
    }
}
