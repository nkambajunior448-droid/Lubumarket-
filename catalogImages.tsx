import React, { useState } from 'react';
import { Package } from 'lucide-react';
import heroBannerImg from '../assets/images/lubumarket_hero_banner_1793480196901.jpg';
import smartphoneImg from '../assets/images/product_smartphone_tech_1793480196633.jpg';
import laptopImg from '../assets/images/product_laptop_computing_1793480197984.jpg';
import fashionImg from '../assets/images/product_fashion_apparel_1793480199001.jpg';
import homeElectronicsImg from '../assets/images/product_home_electronics_1793480199897.jpg';

export const LUBUMARKET_ASSETS = {
  heroBanner: heroBannerImg,
  smartphone: smartphoneImg,
  laptop: laptopImg,
  fashion: fashionImg,
  homeElectronics: homeElectronicsImg,
};

export const PRESET_PRODUCT_GALLERY: Array<{ label: string; url: string }> = [
  { label: 'Téléphone & Audio Studio', url: smartphoneImg },
  { label: 'Ordinateur Portable Pro', url: laptopImg },
  { label: 'Mode & Chaussures Urbaines', url: fashionImg },
  { label: 'Électroménager & Maison', url: homeElectronicsImg },
];

interface SafeImageProps {
  src?: string;
  alt: string;
  className?: string;
}

export const SafeImage: React.FC<SafeImageProps> = ({ src, alt, className = '' }) => {
  const [failed, setFailed] = useState(false);

  if (!src || failed) {
    return (
      <div
        className={`flex flex-col items-center justify-center bg-slate-100 text-slate-500 p-4 text-center ${className}`}
        role="img"
        aria-label={alt}
      >
        <Package className="w-8 h-8 text-[#0F3D8A]/60 mb-2 shrink-0" />
        <span className="text-xs font-medium text-slate-600 line-clamp-2">{alt}</span>
      </div>
    );
  }

  return (
    <img
      src={src}
      alt={alt}
      referrerPolicy="no-referrer"
      onError={() => setFailed(true)}
      className={className}
    />
  );
};
