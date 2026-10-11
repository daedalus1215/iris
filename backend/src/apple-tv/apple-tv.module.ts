import { Module } from '@nestjs/common';
import { AppleTvService } from './domain/apple-tv.service';
import { AppleTvController } from './app/apple-tv.controller';

@Module({
  providers: [AppleTvService],
  controllers: [AppleTvController]
})
export class AppleTvModule {}
