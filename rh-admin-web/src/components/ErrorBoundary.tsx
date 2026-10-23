import { Component, type ErrorInfo, type ReactNode } from 'react';

type Props = {
  children: ReactNode;
  /** Change de valeur → l'erreur est oubliée (ex. changement de page). */
  resetKey?: unknown;
};

type State = { error: Error | null };

/** Évite l'écran blanc : affiche un message et permet de réessayer. */
export default class ErrorBoundary extends Component<Props, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    if (import.meta.env.DEV) console.error('[ErrorBoundary]', error, info.componentStack);
  }

  componentDidUpdate(prev: Props) {
    if (this.state.error && prev.resetKey !== this.props.resetKey) {
      this.setState({ error: null });
    }
  }

  render() {
    if (!this.state.error) return this.props.children;
    return (
      <div className="panel" role="alert" style={{ maxWidth: 560, margin: '2rem auto' }}>
        <h2 className="panel__title">Une erreur est survenue</h2>
        <p className="muted">
          Cette page n’a pas pu s’afficher. Vos données ne sont pas perdues : réessayez, ou
          rechargez la page si le problème persiste.
        </p>
        <div className="modal__actions" style={{ justifyContent: 'flex-start' }}>
          <button type="button" className="btn btn--primary" onClick={() => this.setState({ error: null })}>
            Réessayer
          </button>
          <button type="button" className="btn btn--ghost" onClick={() => window.location.reload()}>
            Recharger la page
          </button>
        </div>
      </div>
    );
  }
}
